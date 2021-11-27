(ns steffan-westcott.otel.context
  "Functions for working with `io.opentelemetry.context.Context` objects."
  (:require [clojure.string :as str]
            [steffan-westcott.otel.api.otel :as otel])
  (:import (java.util HashMap Map)
           (io.opentelemetry.context Context ContextKey ImplicitContextKeyed Scope)
           (io.opentelemetry.context.propagation TextMapSetter TextMapPropagator TextMapGetter)))

(defn current
  "Returns the current context, a thread local `Context` object. If no such
  context exists, the root context is returned instead."
  []
  (Context/current))

(defn ^Scope set-current!
  "Sets the current context. The returned `Scope` must be closed to prevent
  broken traces and memory leaks. [[with-context!]] and [[with-value!]] can
  be used instead of this function to ensure the scope is closed when leaving a
  lexical boundary i.e. a body of forms."
  [^Context context]
  (.makeCurrent context))

(defn close-scope!
  "Closes the given scope."
  [^Scope scope]
  (.close scope))

(defmacro with-context!
  "Sets the current context to be the provided `context`, then evaluates
  `body`. The original current context is restored after body evaluation
  completes."
  [context & body]
  `(let [^Context context# ~context]
     (with-open [_scope# (.makeCurrent context#)]
       ~@body)))

(defmacro with-value!
  "Make a new current context by associating an `ImplicitContextKeyed` instance
  `implicit-context-keyed` then evaluate `body`. The original current context
  is restored after body evaluation completes."
  [implicit-context-keyed & body]
  `(let [^ImplicitContextKeyed value# ~implicit-context-keyed]
     (with-open [_scope# (.makeCurrent value#)]
       ~@body)))

(def ^:private context-key*
  (memoize
    (fn [k]
      (ContextKey/named (name k)))))

(defn ^ContextKey context-key
  "Coerces k to a `ContextKey`."
  [k]
  (if (instance? ContextKey k)
    k
    (context-key* k)))

(defn get-value
  "Returns the value stored in the context for the given context key."
  [^Context context key]
  (.get context (context-key key)))

(defn ^Context assoc-value
  "Associates a key-value with a `context`, returning a new `Context` that
  includes the key-value. Does not use nor affect the current context. Takes
  `key` and `value`, or an `ImplicitContextKeyed` instance which is a value
  that uses a predetermined key."
  ([^Context context ^ImplicitContextKeyed implicit-context-keyed]
   (.with context implicit-context-keyed))
  ([^Context context key value]
   (.with context (context-key key) value)))

(defn root
  "Returns the root context that all other contexts are derived from."
  []
  (Context/root))

;(defn bound-context-fn*
;  "Returns a function which will call function `f` with same arguments and
;  current context set to `context`. If `context` arg is not given, the
;  current context at the time `bound-context-fn*` will be used. The original
;  context is restored after function evaluation completes."
;  ([f]
;   (bound-context-fn* f (current)))
;  ([f ^Context context]
;   (fn [& args]
;     (with-context! context (apply f args)))))

(defn current-context-interceptor
  "Returns a Pedestal interceptor that will on entry set the current context to
  the value that has key `context-key` in the interceptor context map. The
  original value of current context is restored on evaluation on interceptor
  exit (either `leave` or `error`). The `Scope` of the set context is stored
  in the interceptor context map with key `scope-key`."
  [context-key scope-key]
  {:name  ::current-context
   :enter (fn [ctx]
            (let [scope (set-current! (get ctx context-key))]
              (assoc ctx scope-key scope)))
   :leave (fn [ctx]
            (close-scope! (get ctx scope-key))
            ctx)
   :error (fn [ctx e]
            (close-scope! (get ctx scope-key))
            (assoc ctx :io.pedestal.interceptor.chain/error e))})

(def ^:private map-setter
  (reify TextMapSetter
    (set [_ carrier key value]
      (.put ^Map carrier key value))))

(def ^:private map-getter
  (reify TextMapGetter
    (keys [_ carrier]
      (keys carrier))
    (get [_ carrier key]
      (some-> (get carrier key)
              (str/split #",")
              first
              str/trim))))

(defn ->headers
  "Returns a map to merge into the headers of an HTTP request for the purpose
  of context propagation i.e. transfer context to a remote server. May take an
  optional map argument as follows:

  | key                  | description |
  |----------------------|-------------|
  |`:context`            | Context to propagate (default: current context).
  |`:text-map-propagator`| Propagator used to create headers map entries (default: propagator set in global `OpenTelemetry` instance)."
  ([]
   (->headers {}))
  ([{:keys [^Context context ^TextMapPropagator text-map-propagator]
     :or   {context             (current)
            text-map-propagator (otel/get-text-map-propagator)}}]
   (let [carrier (HashMap.)]
     (.inject text-map-propagator context carrier map-setter)
     (into {} carrier))))

(defn ^Context headers->merged-context
  "Returns a context formed by extracting a propagated context from a map
  `headers` and merging with another context i.e. accept context transfer from
  a remote server. May take an option map as follows:

  | key                  | description |
  |----------------------|-------------|
  |`:context`            | Context to merge with (default: current context).
  |`:text-map-propagator`| Propagator used to extract data from the headers map (default: propagator set in global `OpenTelemetry` instance)."
  ([headers]
   (headers->merged-context headers {}))
  ([headers {:keys [^Context context ^TextMapPropagator text-map-propagator]
             :or   {context             (current)
                    text-map-propagator (otel/get-text-map-propagator)}}]
   (.extract text-map-propagator context headers map-getter)))

(comment

  )