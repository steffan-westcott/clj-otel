(ns steffan-westcott.clj-otel.api.trace.span
  "Functions for manipulating spans."
  (:require [clojure.main :as main]
            [steffan-westcott.clj-otel.api.attributes :as attr]
            [steffan-westcott.clj-otel.api.otel :as otel]
            [steffan-westcott.clj-otel.config :refer [config]]
            [steffan-westcott.clj-otel.context :as context]
            [steffan-westcott.clj-otel.util :as util])
  (:import (io.opentelemetry.api OpenTelemetry)
           (io.opentelemetry.api.trace Span SpanBuilder SpanContext SpanKind StatusCode Tracer)
           (io.opentelemetry.context Context)
           (io.opentelemetry.semconv.trace.attributes SemanticAttributes)))

(def ^:private default-library
  (get-in config [:defaults :instrumentation-library]))

(defn get-tracer
  "Builds and returns a `io.opentelemetry.api.trace.Tracer` instance. May take
  an option map as follows:

  | key             | description |
  |-----------------|-------------|
  |`:name`          | Name of the *instrumentation* library, not the *instrumented* library e.g. `\"io.opentelemetry.contrib.mongodb\"` (default: See `config.edn` resource file).
  |`:version`       | Instrumentation library version e.g. `\"1.0.0\"` (default: See `config.edn` resource file).
  |`:schema-url`    | URL of OpenTelemetry schema used by this instrumentation library (default: See `config.edn` resource file).
  |`:open-telemetry`| `OpenTelemetry` instance to get tracer from (default: global `OpenTelemetry` instance)."
  ([]
   (get-tracer {}))
  ([{:keys [name version schema-url open-telemetry]
     :or   {name       (:name default-library)
            version    (:version default-library)
            schema-url (:schema-url default-library)}}]
   (let [^OpenTelemetry otel (or open-telemetry (otel/get-global-otel!))
         builder (cond-> (.tracerBuilder otel name)
                   version    (.setInstrumentationVersion version)
                   schema-url (.setSchemaUrl schema-url))]
     (.build builder))))

(defn noop-tracer
  "Gets a no-op tracer."
  []
  (get-tracer {:open-telemetry (otel/get-noop)}))

(defonce ^:private default-tracer (atom nil))

(defn set-default-tracer!
  "Sets the default `io.opentelemetry.api.trace.Tracer` instance used when
  creating spans. Returns `tracer`. See also [[get-tracer]]."
  [tracer]
  (reset! default-tracer tracer))

(defn- get-default-tracer!
  "Returns the default tracer if not nil. Otherwise, gets a tracer using
  defaults and sets this as the default tracer."
  ^Tracer []
  (if-let [tracer @default-tracer]
    tracer
    (set-default-tracer! (get-tracer))))

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

(defn get-span
  "Gets the span from a given context, or the current context if none is given.
  If no span is found in the context (or no context is available), a
  non-recording, non-exporting span is returned."
  (^Span []
   (Span/current))
  (^Span [context]
   (Span/fromContext context)))

(defprotocol ^:private AsSpanContext
  (^:no-doc span-context [x]))

(extend-protocol AsSpanContext
 SpanContext
   (span-context [x]
     x)
 Span
   (span-context [x]
     (.getSpanContext x))
 Context
   (span-context [x]
     (.getSpanContext (get-span x))))

(defn get-span-context
  "Returns the given `SpanContext`, or extracts it from the given span,
  context or current context if no argument is given."
  ([]
   (get-span-context (get-span)))
  ([x]
   (span-context x)))

(defn- add-link
  [^SpanBuilder builder [sc attributes]]
  (if-let [span-context (get-span-context sc)]
    (if attributes
      (.addLink builder span-context (attr/->attributes attributes))
      (.addLink builder span-context))
    builder))

(defn- add-links
  ^SpanBuilder [^SpanBuilder builder links]
  (reduce add-link builder links))

(defn new-span!
  "Low level function that starts a new span and returns the context containing
  the new span. Does not mutate the current context. The span must be ended by
  evaluating [[end-span!]] to avoid broken traces and memory leaks. Use higher
  level helpers [[with-span!]], [[with-span-binding]] and [[async-span]]
  instead of this function to manage the context and reliably end the span.
  Takes an options map as follows:

  | key         | description |
  |-------------|-------------|
  |`:tracer`    | `io.opentelemetry.api.trace.Tracer` used to create the span (default: default tracer, as set by [[set-default-tracer!]]; if no default tracer has been set, one will be set with default config).
  |`:name`      | Span name (default: `\"\"`).
  |`:parent`    | Context used to take parent span. If `nil` or no span is available in the context, the root context is used instead (default: use current context).
  |`:links`     | Collection of links to add to span. Each link is `[sc]` or `[sc attr-map]`, where `sc` is a `SpanContext`, `Span` or `Context` containing the linked span and `attr-map` is a map of attributes of the link (default: no links).
  |`:attributes`| Map of additional attributes for the span (default: no attributes).
  |`:thread`    | Thread identified as that which started the span, or `nil` for no thread. Data on this thread is merged with the `:attributes` value (default: current thread).
  |`:source`    | Map describing source code where span is started. Optional keys are `:fn`, `:ns`, `:line` and `:file` (default: {}).
  |`:span-kind` | Span kind, one of `:internal`, `:server`, `:client`, `:producer`, `:consumer` (default: `:internal`). See also `SpanKind`.
  |`:timestamp` | Start timestamp for the span. Value is either an `Instant` or vector `[amount ^TimeUnit unit]` (default: current timestamp)."
  ^Context
  [{:keys [^Tracer tracer name parent links attributes ^Thread thread source span-kind timestamp]
    :or   {name       ""
           parent     (context/current)
           attributes {}
           thread     (Thread/currentThread)
           source     {}}}]
  (let [tracer'        (or tracer (get-default-tracer!))
        parent-context (or parent (context/root))
        {:keys [fn ns line file]} source
        default-attributes (cond-> {}
                             thread (assoc SemanticAttributes/THREAD_NAME
                                           (.getName thread)
                                           SemanticAttributes/THREAD_ID
                                           (.getId thread))
                             fn     (assoc SemanticAttributes/CODE_FUNCTION fn)
                             ns     (assoc SemanticAttributes/CODE_NAMESPACE ns)
                             line   (assoc SemanticAttributes/CODE_LINENO line)
                             file   (assoc SemanticAttributes/CODE_FILEPATH file))
        attributes'    (merge default-attributes attributes)
        builder        (cond-> (.spanBuilder tracer' name)
                         :always   (.setParent parent-context)
                         links     (add-links links)
                         :always   (.setAllAttributes (attr/->attributes attributes'))
                         span-kind (.setSpanKind (keyword->SpanKind span-kind))
                         timestamp (as-> b (let [[amount unit] (util/timestamp timestamp)]
                                             (.setStartTimestamp b amount unit))))
        span           (.startSpan builder)]
    (context/assoc-value parent-context span)))

(defn- add-event!
  [^Span span
   {:keys [^String name attributes timestamp]
    :or   {name       ""
           attributes {}}}]
  (let [attrs (attr/->attributes attributes)]
    (if timestamp
      (let [[amount unit] (util/timestamp timestamp)]
        (.addEvent span name attrs amount unit))
      (.addEvent span name attrs))))

(defn- add-ex-data!
  [^Span span
   {:keys [exception escaping? attributes]
    :or   {attributes {}}}]
  (let [attrs (cond-> attributes
                (some? escaping?) (assoc SemanticAttributes/EXCEPTION_ESCAPED (boolean escaping?)))]
    (.recordException span exception (attr/->attributes attrs))))

(defn add-span-data!
  "Adds data to a span. All data values documented here are optional unless
  otherwise noted as required. Takes a top level option map as follows:

  Top level option map

  | key         | description |
  |-------------|-------------|
  |`:context`   | Context containing span to add data to (default: current context).
  |`:name`      | Name to set span to.
  |`:status`    | Option map (see below) describing span status to set.
  |`:attributes`| Map of additional attributes to merge in the span.
  |`:event`     | Option map (see below) describing an event to add to the span.
  |`:ex-data`   | Option map (see below) describing an exception event to add to the span.

  `:status` option map

  | key          | description |
  |--------------|-------------|
  |`:code`       | Status code, either `:ok` or `:error` (required).
  |`:description`| Status description string, only applicable with `:error` status code.

  `:event` option map

  | key         | description |
  |-------------|-------------|
  |`:name`      | Event name (required).
  |`:attributes`| Map of attributes to attach to event.
  |`:timestamp` | Event timestamp, value is either an `Instant` or vector `[amount ^TimeUnit unit]`.

  `:ex-data` option map

  | key         | description |
  |-------------|-------------|
  |`:exception` | Exception instance (required).
  |`:escaping?` | Optional value, true if exception is escaping the span's scope, false if exception is caught within the span's scope and not rethrown.
  |`:attributes`| Map of additional attributes to attach to exception event."
  [{:keys [context name status attributes event ex-data]
    :or   {context (context/current)}}]
  (let [span (get-span context)]
    (cond-> span
      name       (.updateName name)
      status     (.setStatus (keyword->StatusCode (:code status)) (:description status))
      attributes (.setAllAttributes (attr/->attributes attributes))
      event      (add-event! event)
      ex-data    (add-ex-data! ex-data))))

(defn add-exception!
  "Adds an event describing `exception` to a span. By default, exception data
  (as per `ex-info`) are added as attributes to the event. If the exception is
  escaping the span's scope, the span's status is also set to `:error` with the
  exception triage summary as the error description. May take an option map as
  follows:

  | key         | description |
  |-------------|-------------|
  |`:context`   | Context containing span to add exception event to (default: current context).
  |`:escaping?` | If true, exception is escaping the span's scope; if false, exception is caught within the span's scope and not rethrown (default: `true`).
  |`:attributes`| Either a function which takes `exception` and returns a map of additional attributes for the event, or a map of additional attributes (default: `ex-data`)."
  ([exception]
   (add-exception! exception {}))
  ([exception
    {:keys [context escaping? attributes]
     :or   {context    (context/current)
            escaping?  true
            attributes ex-data}}]
   (let [triage      (main/ex-triage (Throwable->map exception))
         attributes' (if (fn? attributes)
                       (attributes exception)
                       attributes)
         attrs       (into triage attributes')
         status      (when escaping?
                       {:code        :error
                        :description (main/ex-str triage)})]
     (add-span-data! (cond-> {:context context
                              :ex-data {:exception  exception
                                        :escaping?  escaping?
                                        :attributes attrs}}
                       status (assoc :status status))))))

(defn add-interceptor-exception!
  "Adds an event describing an interceptor exception `e` to a span. Attributes
  identifying the interceptor and stage are added to the event. Also, by
  default exception data (as per `ex-info`) are added as attributes. If the
  exception is escaping the span's scope, the span's status is also set to
  `:error` with the wrapped exception triage summary as the error description.
  May take an option map as follows:

  | key         | description |
  |-------------|-------------|
  |`:context`   | Context containing span to add exception event to (default: current context)
  |`:escaping?` | If true, exception is escaping the span's scope; if false, exception is caught within the span's scope and not rethrown (default: `true`).
  |`:attributes`| Either a function which takes `exception` and returns a map of additional attributes for the event, or a map of additional attributes (default: `ex-data`)."
  ([e]
   (add-interceptor-exception! e {}))
  ([e
    {:keys [context escaping? attributes]
     :or   {context    (context/current)
            escaping?  true
            attributes ex-data}}]
   (let [{:keys [exception interceptor stage]
          :or   {exception e}}
         (ex-data e)

         attrs (into {:io.pedestal.interceptor.chain/interceptor interceptor
                      :io.pedestal.interceptor.chain/stage       stage}
                     (if (fn? attributes)
                       (attributes exception)
                       attributes))]
     (add-exception! exception
                     {:context    context
                      :escaping?  escaping?
                      :attributes attrs}))))

(defn end-span!
  "Low level function that ends a span, previously started by [[new-span!]].
  Does not mutate the current context. Takes an options map as follows:

  | key        | description |
  |------------|-------------|
  |`:context`  | Context containing span to end (default: current context)
  |`:timestamp`| Span end timestamp. Value is either an `Instant` or vector `[amount ^TimeUnit unit]` (default: current timestamp)."
  [{:keys [context timestamp]
    :or   {context (context/current)}}]
  (let [span (get-span context)]
    (if timestamp
      (let [[amount unit] (util/timestamp timestamp)]
        (.end span amount unit))
      (.end span))))

#_{:clj-kondo/ignore [:missing-docstring]}
(defmacro ^:no-doc with-span-binding'
  [[context span-opts] & body]
  `(let [~context (new-span! ~span-opts)]
     (try
       ~@body
       (catch Throwable e#
         (add-exception! e# {:context ~context})
         (throw e#))
       (finally
         (end-span! {:context ~context})))))

(defmacro with-span-binding
  "Starts a new span, binds `context` to the new context containing the span
  and evaluates `body`. The span is ended on completion of body evaluation.
  It is expected `body` provides a synchronous result, use [[async-span]]
  instead for working with asynchronous functions. Does not use nor set the
  current context. `span-opts` is a span options map, the same as for
  [[new-span!]], except that the default values for `:line`, `:file` and `:ns`
  for the `:source` option map are set from the place `with-span-binding` is
  evaluated. See also [[with-span!]]."
  [[context span-opts] & body]
  `(let [span-opts# ~span-opts
         source#    (into {:line ~(:line (meta &form))
                           :file ~*file*
                           :ns   ~(str *ns*)}
                          (:source span-opts#))
         span-opts# (assoc span-opts# :source source#)]
     (with-span-binding' [~context span-opts#]
       ~@body)))

(defmacro with-span!
  "Starts a new span, sets the current context to the new context containing
  the span and evaluates `body`. The current context is restored to its
  previous value and the span is ended on completion of body evaluation.
  It is expected `body` provides a synchronous result, use [[async-span]]
  instead for working with asynchronous functions. `span-opts` is a span
  options map, the same as for [[new-span!]], except that the default values
  for `:line`, `:file` and `:ns` for the `:source` option map are set from the
  place `with-span!` is evaluated. See also [[with-span-binding]]."
  [span-opts & body]
  `(let [span-opts# ~span-opts
         source#    (into {:line ~(:line (meta &form))
                           :file ~*file*
                           :ns   ~(str *ns*)}
                          (:source span-opts#))
         span-opts# (assoc span-opts# :source source#)]
     (with-span-binding' [context# span-opts#]
       (context/with-context! context#
         ~@body))))

#_{:clj-kondo/ignore [:missing-docstring]}
(defn ^:no-doc async-span'
  [span-opts f respond raise]
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
               (add-span-data! {:context context
                                :status  {:code :error}}))
             (end-span! {:context context})
             (raise e)))
        (catch Throwable e
          (add-exception! e {:context context})
          (end-span! {:context context})
          (raise e))))
    (catch Throwable e
      (raise e))))

(defmacro async-span
  "Starts a new span and immediately returns evaluation of function `f`.
  `respond`/`raise` are callback functions to be evaluated later on a
  success/failure result. The span is ended just before either callback is
  evaluated or `f` itself throws an exception. Does not use nor mutate the
  current context. This is a low-level function intended for adaption for use
  with any async library that can work with callbacks.

  `span-opts` is the same as for [[new-span!]], except that the default values
  for `:line`, `:file` and `:ns` for the `:source` option map are set from the
  place `async-span` is evaluated. `f` must take arguments `[context respond*
  raise*]` where `context` is a context containing the new span, `respond*` and
  `raise*` are callback functions to be used by `f`. All callback functions
  take a single argument, `raise` and `raise*` take a `Throwable` instance."
  [span-opts f respond raise]
  `(let [span-opts# ~span-opts
         source#    (into {:line ~(:line (meta &form))
                           :file ~*file*
                           :ns   ~(str *ns*)}
                          (:source span-opts#))
         span-opts# (assoc span-opts# :source source#)]
     (async-span' span-opts# ~f ~respond ~raise)))

(defn span-interceptor
  "Returns a Pedestal interceptor that will on entry start a new span and add
  the context containing the new span to the interceptor map with key
  `context-key`. `span-opts-fn` is a function which takes the interceptor
  context and returns an options map for the new span, as for [[new-span!]].
  The span is ended on interceptor exit (either `leave` or `error`). Does not
  use nor set the current context. This is not specific to HTTP service
  handling, see
  [[steffan-westcott.clj-otel.api.trace.http/server-span-interceptors]] for
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
