(ns steffan-westcott.otel.api.trace.span
  "Functions for manipulating spans.

  A span represents a single operation and a tree of spans form a trace. For
  example, a distributed trace may describe the cascade of operations
  throughout a system of services initiated by a single external HTTP request.

  See the [span specification](https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/trace/api.md#span)
  for more conceptual information."
  (:require [steffan-westcott.otel.api.otel :as otel]
            [steffan-westcott.otel.api.attributes :as attr]
            [steffan-westcott.otel.context :as context]
            [clojure.main :as main]
            [steffan-westcott.otel.util :as util])
  (:import (io.opentelemetry.api.trace SpanBuilder Span SpanContext StatusCode SpanKind Tracer)
           (io.opentelemetry.context Context)
           (io.opentelemetry.semconv.trace.attributes SemanticAttributes)
           (io.opentelemetry.api OpenTelemetry)))

(defn get-tracer
  "Gets a tracer. Takes an option map as follows:

  | key             | description |
  |-----------------|-------------|
  |`:name`          | Name of the instrumentation library, not the instrument*ed* library e.g. `\"io.opentelemetry.contrib.mongodb\"` (default: \"default-tracer\").
  |`:version`       | Instrumentation library version e.g. `\"1.0.0\"` (default: nil).
  |`:schema-url`    | URL of OpenTelemetry schema used by this instrumentation library (default: nil).
  |`:open-telemetry`| [[OpenTelemetry]] instance to get tracer from (default: global instance)."
  [{:keys [name version schema-url open-telemetry]
    :or   {name "default-tracer"}}]
  (let [^OpenTelemetry otel (or open-telemetry (otel/get-global-otel!))
        builder (cond-> (.tracerBuilder otel name)
                        version (.setInstrumentationVersion version)
                        schema-url (.setSchemaUrl schema-url))]
    (.build builder)))

(defn noop-tracer
  "Gets a no-op tracer."
  []
  (get-tracer {:open-telemetry (otel/get-noop)}))

(defonce ^:private default-tracer (atom (noop-tracer)))

(defn set-default-tracer!
  "Sets the default tracer used when creating spans."
  [tracer]
  (reset! default-tracer tracer))

(def ^:private keyword->StatusCode
  {:unset StatusCode/UNSET
   :ok    StatusCode/OK
   :error StatusCode/ERROR})

(def ^:private keyword->SpanKind
  {:internal SpanKind/INTERNAL
   :server   SpanKind/SERVER
   :client   SpanKind/CLIENT
   :producer SpanKind/PRODUCER
   :consumer SpanKind/CONSUMER})

(defn ^Span get-span
  "Gets the span from a given context, or the current context if none is given.
  If no span is found in the context (or no context is available), a
  non-recording, non-exporting span is returned."
  ([]
   (Span/current))
  ([context]
   (Span/fromContext context)))

(defn get-span-context
  "Returns the given [[SpanContext]], or extracts it from the given span
  or context. With no arg it uses the current context."
  ([]
   (get-span-context (get-span)))
  ([x]
   (cond
     (instance? SpanContext x) x
     (instance? Span x) (.getSpanContext ^Span x)
     (instance? Context x) (.getSpanContext (get-span ^Context x)))))

(defn- add-link [^SpanBuilder builder [sc attributes]]
  (if-let [span-context (get-span-context sc)]
    (if attributes
      (.addLink builder span-context (attr/map->Attributes attributes))
      (.addLink builder span-context))
    builder))

(defn- ^SpanBuilder add-links [^SpanBuilder builder links]
  (reduce add-link builder links))

(defn ^Context new-span!
  "Low level function that starts a new span and returns the context containing
  the new span. Does not mutate the current context. The span must be ended by
  calling [[end-span!]] to avoid broken traces and memory leaks. Use higher
  level helpers [[with-span!]], [[with-span-binding]] and [[async-span]]
  instead of this function to manage the context and reliably end the span.
  Takes an options map as follows:

  | key         | description |
  |-------------|-------------|
  |`:tracer`    | [[Tracer]] used to create the span (default: default tracer).
  |`:name`      | Span name (default: \"\").
  |`:parent`    | Context used to take parent span. If `nil` or no span is available in the context, the root context is used instead (default: use current context).
  |`:links`     | Collection of links to add to span. Each link is `[sc]` or `[sc attr-map]`, where `sc` is a [[SpanContext]], [[Span]] or [[Context]] containing the linked span and `attr-map` is a map of attributes of the link (default: no links).
  |`:attributes`| Map of additional attributes for the span (default: no attributes).
  |`:thread`    | Thread identified as that which started the span, or `nil` for no thread. Data on this thread is merged with the `:attributes` value (default: current thread).
  |`:span-kind` | Span kind, one of `:internal`, `:server`, `:client`, `:producer`, `:consumer` (default: `:internal`). See also [[SpanKind]].
  |`:timestamp` | Start timestamp for the span. Value is either an [[Instant]] or vector `[amount ^TimeUnit unit]` (default: current timestamp)."
  [{:keys [^Tracer tracer name parent links attributes ^Thread thread span-kind timestamp]
    :or   {tracer     @default-tracer
           name       ""
           parent     (context/current)
           attributes {}
           thread     (Thread/currentThread)}}]
  (let [parent-context (or parent (context/root))
        default-attributes (if thread
                             {SemanticAttributes/THREAD_NAME (.getName thread)
                              SemanticAttributes/THREAD_ID   (.getId thread)}
                             {})
        attributes' (merge default-attributes attributes)
        builder (cond-> (.spanBuilder tracer name)
                        :always (.setParent parent-context)
                        links (add-links links)
                        :always (.setAllAttributes (attr/map->Attributes attributes'))
                        span-kind (.setSpanKind (keyword->SpanKind span-kind))
                        timestamp (as-> b (let [[amount unit] (util/timestamp timestamp)]
                                            (.setStartTimestamp b amount unit))))
        span (.startSpan builder)]
    (context/assoc-value parent-context span)))

(defn- add-event!
  [^Span span {:keys [^String name attributes timestamp]
               :or   {name "" attributes {}}}]
  (let [attrs (attr/map->Attributes attributes)]
    (if timestamp
      (let [[amount unit] (util/timestamp timestamp)]
        (.addEvent span name attrs amount unit))
      (.addEvent span name attrs))))

(defn- add-ex-data!
  [^Span span {:keys [exception escaping? attributes]
               :or   {attributes {}}}]
  (let [attrs (cond-> attributes
                      (some? escaping?) (assoc SemanticAttributes/EXCEPTION_ESCAPED (boolean escaping?)))]
    (.recordException span exception (attr/map->Attributes attrs))))

(defn add-span-data!
  "Adds data to a span. All data values documented here are optional unless
  otherwise noted as required. Takes a top level option map as follows:

  Top level option map

  | key         | description |
  |-------------|-------------|
  |`:context`   | Context containing span to data to (default: current context).
  |`:name`      | Name to set span to.
  |`:status`    | Option map (see below) describing span status to set.
  |`:attributes`| Map of additional attributes to merge in the span.
  |`:event`     | Option map (see below) describing an event to add to the span.
  |`:ex-data`   | Option map (see below) describing an exception event to add to the span.

  `:status` option map

  | key          | description |
  |--------------|-------------|
  |`:code`       | Status code, either `:ok` or `:error` (required).
  |`:description`| Status description, only applicable with `:error` status code.

  `:event` option map

  | key         | description |
  |-------------|-------------|
  |`:name`      | Event name (required).
  |`:attributes`| Map of attributes to attach to event.
  |`:timestamp` | Event timestamp, value is either an [[Instant]] or vector `[amount ^TimeUnit unit]`.

  `:ex-data` option map

  | key         | description |
  |-------------|-------------|
  |`:exception` | Exception instance (required).
  |`:escaping?` | Optional boolean value, `true` if exception is escaping the span's scope, 'false' if exception is caught within the span's scope and not rethrown.
  |`:attributes`| Map of additional attributes to attach to exception event."
  [{:keys [context name status attributes event ex-data]
    :or   {context (context/current)}}]
  (let [span (get-span context)]
    (cond-> span
            name (.updateName name)
            status (.setStatus (keyword->StatusCode (:code status)) (:description status))
            attributes (.setAllAttributes (attr/map->Attributes attributes))
            event (add-event! event)
            ex-data (add-ex-data! ex-data))))

(defn add-exception!
  "Adds an event describing `exception` to a span. If the exception is escaping
  the span's scope, the span's status is also set to `:error` with the
  exception triage summary as the error description. May take an option map as
  follows:

  | key         | description |
  |-------------|-------------|
  |`:context`   | Context containing span to add exception event to (default: current context).
  |`:escaping?` | If true, exception is escaping the span's scope; if false, exception is caught within the span's scope and not rethrown (default: `true`).
  |`:attributes`| Map of additional attributes for the event (default: `{}`)."
  ([exception]
   (add-exception! exception {}))
  ([exception {:keys [context escaping? attributes]
               :or   {context (context/current) escaping? true attributes {}}}]
   (let [triage (main/ex-triage (Throwable->map exception))
         attrs (merge triage attributes)
         status (when escaping? {:code :error :description (main/ex-str triage)})]
     (add-span-data! (cond-> {:context context
                              :ex-data {:exception exception :escaping? escaping? :attributes attrs}}
                             status (assoc :status status))))))

(defn add-interceptor-exception!
  "Adds an event describing a wrapped interceptor exception `e` to a span and
  sets the span's status to `:error` with the wrapped exception triage summary
  as the error description. May take an option map as follows:

  | key         | description |
  |-------------|-------------|
  |`:context`   | Context containing span to add exception event to (default: current context)
  |`:attributes`| Map of additional attributes for the event (default: `{}`)."
  ([e]
   (add-interceptor-exception! e {}))
  ([e {:keys [context attributes]
       :or   {context (context/current) attributes {}}}]
   (let [{:keys [exception interceptor stage] :or {exception e}} (ex-data e)
         attrs (merge {:io.pedestal.interceptor.chain/interceptor interceptor
                       :io.pedestal.interceptor.chain/stage       stage}
                      attributes)]
     (add-exception! exception {:context context :attributes attrs}))))

(defn end-span!
  "Low level function that ends a span, previously started by calling
  [[new-span!]]. Does not mutate the current context. Takes an options map as
  follows:

  | key        | description |
  |------------|-------------|
  |`:context`  | Context containing span to end (default: current context)
  |`:timestamp`| Span end timestamp. Value is either an [[Instant]] or vector `[amount ^TimeUnit unit]` (default: current timestamp)."
  [{:keys [context timestamp]
    :or   {context (context/current)}}]
  (let [span (get-span context)]
    (if timestamp
      (let [[amount unit] (util/timestamp timestamp)]
        (.end span amount unit))
      (.end span))))

(defmacro with-span-binding
  "Starts a new span, binds `context` to the new context containing the span
  and evaluates `body`. The span is ended on completion of body evaluation.
  It is expected `body` provides a synchronous result, use [[async-span]]
  instead for working with asynchronous functions. Does not use nor set the
  current context. `span-opts` is an option map, the same as for [[new-span!]].
  See also [[with-span!]]."
  [[context span-opts] & body]
  `(let [~context (new-span! ~span-opts)]
     (try
       ~@body
       (catch Throwable e#
         (add-exception! e# {:context ~context})
         (throw e#))
       (finally
         (end-span! {:context ~context})))))

(defmacro with-span!
  "Starts a new span, sets the current context to the new context containing
  the span and evaluates `body`. The current context is restored to its
  previous value and the span is ended on completion of body evaluation.
  It is expected `body` provides a synchronous result, use [[async-span]]
  instead for working with asynchronous functions. `span-opts` is an option
  map, the same as for [[new-span!]]. See also [[with-span-binding]]."
  [span-opts & body]
  `(with-span-binding [context# ~span-opts]
     (context/with-context! context# ~@body)))

(defn async-span
  "Starts a new span and returns evaluation of function `f` with
  success/exception callback functions `respond`/`raise`. The span is ended
  just before either callback is evaluated or `f` itself throws an exception.
  Does not use nor mutate the current context. This is a low-level function
  intended for adaption for use with any async library that can work with
  callbacks.

  Async function execution in a span may occur on any of a number of threads.
  For this reason async function executions must retain a reference to the
  associated context as it is not possible to use the (default and unrelated)
  current context bound to the thread. Some functions in this library take a
  `:context` or `:parent` option to indicate which context to use. Contexts
  are immutable. Creating a new span (or otherwise adding a new value to the
  context) creates a new child context.

  `span-opts` is the same as for [[new-span!]]. `f` must take arguments
  `[context respond* raise*]` where `context` is a context containing the new
  span, `respond*` and `raise*` are callback functions to be used by `f`. All
  callback functions take a single argument, `raise` and `raise*` take a
  `Throwable` instance."
  ([span-opts f respond raise]
   (try
     (let [context (new-span! span-opts)]
       (try
         (f context
            (fn [response]
              (end-span! {:context context})
              (respond response))
            (fn [e]
              (if (instance? Throwable e)
                (add-exception! e {:context context})
                (add-span-data! {:context context :status {:code :error}}))
              (end-span! {:context context})
              (raise e)))
         (catch Throwable e
           (add-exception! e {:context context})
           (end-span! {:context context})
           (raise e))))
     (catch Throwable e
       (raise e)))))

(defn span-interceptor
  "Returns a Pedestal interceptor that will on entry start a new span and add
  the context containing the new span to the interceptor map with key
  `context-key`. `span-opts-fn` is a function which takes the interceptor
  context and returns an options map for the new span, as for [[new-span!]].
  The span is ended on interceptor exit (either `leave` or `error`). Does not
  use nor set the current context. This is not specific to HTTP service
  handling, see
  [[steffan-westcott.otel.api.trace.http/server-span-interceptors]] for
  adding HTTP server span support to HTTP services."
  [context-key span-opts-fn]
  {:name  ::span
   :enter (fn [ctx]
            (let [context (new-span! (span-opts-fn ctx))]
              (assoc ctx context-key context)))
   :leave (fn [ctx]
            (end-span! {:context (get ctx context-key)})
            ctx)
   :error (fn [ctx e]
            (let [context (get ctx context-key)]
              (add-interceptor-exception! e {:context context})
              (end-span! {:context context})
              (assoc ctx :io.pedestal.interceptor.chain/error e)))})

(defn current-context-interceptor
  "Returns a Pedestal interceptor that will on entry set the current context to
  the value that has key `context-key` in the interceptor context map. The
  original value of current context is restored on evaluation on interceptor
  exit (either `leave` or `error`). The [[Scope]] of the set context is stored
  with key `scope-key`."
  [context-key scope-key]
  {:name  ::current-context
   :enter (fn [ctx]
            (let [scope (context/set-current! (get ctx context-key))]
              (assoc ctx scope-key scope)))
   :leave (fn [ctx]
            (context/close-scope! (get ctx scope-key))
            ctx)
   :error (fn [ctx e]
            (context/close-scope! (get ctx scope-key))
            (assoc ctx :io.pedestal.interceptor.chain/error e))})


;(defmacro in-span
;  [[context opts respond raise :as args] & body]
;  (case (count args)
;    (1 2) `(let [~context (new-span! (assoc ~opts :parent ~context))]
;             (try
;               ~@body
;               (catch Throwable e#
;                 (add-escaping-exception! {:context ~context :exception e#})
;                 (throw e#))
;               (finally
;                 (end-span! {:context ~context}))))
;    4 `(try
;         (let [~context (new-span! (assoc ~opts :parent ~context))]
;           (try
;             (let [~respond (fn [response#]
;                              (end-span! {:context ~context})
;                              (~respond response#))
;                   ~raise (fn [e#]
;                            (add-escaping-exception! {:context ~context :exception e#})
;                            (end-span! {:context ~context})
;                            (~raise e#))]
;               ~@body)
;             (catch Throwable e#
;               (add-escaping-exception! {:context ~context :exception e#})
;               (end-span! {:context ~context})
;               (~raise e#))))
;         (catch Throwable e#
;           (~raise e#)))))

;(defmacro in-client-span
;  [[context opts respond raise :as args] & body]
;  (case (count args)
;    (1 2) `(in-span [~context (assoc ~opts :span-kind :client)] ~@body)
;    4 `(in-span [~context (assoc ~opts :span-kind :client) ~respond ~raise] ~@body)))

;(defmacro bind-span
;  [[context opts] & body]
;  `(with-manual-span [context# ~opts]
;                     (binding [~context context#]
;                       ~@body)))

;(defn wrap-client-span
;  "Wraps fn `handler` with a client span with name `span-name`.
;  `handler` is expected to take args `[context args]` or
;  `[context args respond raise]`."
;  [handler span-name]
;  (fn
;    ([context args]
;     (let [span-context (trace/new-span! {:name      span-name
;                                          :span-kind :client
;                                          :parent    context})]
;       (try
;         (handler span-context args)
;         (catch Throwable e
;           (add-exception-and-error! span-context e)
;           (throw e))
;         (finally
;           (trace/end-span! {:context span-context})))))
;    ([context args respond raise]
;     (try
;       (let [span-context (trace/new-span! {:name      span-name
;                                            :span-kind :client
;                                            :parent    context})]
;         (try
;           (handler span-context
;                    args
;                    (fn [response]
;                      (trace/end-span! {:context span-context})
;                      (respond response))
;                    (fn [e]
;                      (add-exception-and-error! span-context e)
;                      (trace/end-span! {:context span-context})
;                      (raise e)))
;           (catch Throwable e
;             (add-exception-and-error! span-context e)
;             (trace/end-span! {:context span-context})
;             (raise e))))
;       (catch Throwable e
;         (raise e))))))
;
;(defmacro defn-client-span
;  "Like defn, but wraps function with a client span. Expects
;  function to take args `[context args]` or
;  `[context args respond raise]`."
;  [fn-name args & body]
;  `(def ~fn-name (wrap-client-span (fn ~args ~@body) "Calling it")))

(comment
  )