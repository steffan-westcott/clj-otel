(ns steffan-westcott.otel.context
  "Functions for working with [[Context]] objects.

  Contexts are a medium for carrying values across API boundaries and threads.
  Contexts are used to propagate correlation identifiers from parent to child
  spans to enable assembly of traces.

  Contexts are immutable containers of keyed values. A new context is created
  from an existing context with the addition of a new key-value association.

  The \"current context\" is a thread local Context object, used as a default
  for many API functions in `clj-otel` and the underlying Java implementation
  `opentelemetry-java`. The current context is safe to use in synchronous code,
  but is not suitable for use in asynchronous code."
  (:require [clojure.string :as str]
            [steffan-westcott.otel.api.otel :as otel])
  (:import (java.util HashMap)
           (io.opentelemetry.context Context ContextKey ImplicitContextKeyed Scope)
           (io.opentelemetry.context.propagation TextMapSetter TextMapPropagator TextMapGetter)))

(defn current
  "Returns the current context, bound to the current thread. If no context is
  bound to the current thread, the root context is returned instead."
  []
  (Context/current))

(defn ^Scope set-current!
  "Set the current context. The returned [[Scope]] must be closed to prevent
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
  "Set the current context to be the provided `context`, then evaluate `body`.
  The original current context is restored after body evaluation completes."
  [context & body]
  `(let [^Context context# ~context]
     (with-open [_scope# (.makeCurrent context#)]
       ~@body)))

(defmacro with-value!
  "Make a new current context by associating `implicit-context-keyed-value`,
  then evaluate `body`. The original current context is restored after body
  evaluation completes."
  [implicit-context-keyed-value & body]
  `(let [^ImplicitContextKeyed value# ~implicit-context-keyed-value]
     (with-open [_scope# (.makeCurrent value#)]
       ~@body)))

(def ^:private context-key*
  (memoize
    (fn [k]
      (ContextKey/named (name k)))))

(defn ^ContextKey context-key
  "Coerces k to a [[ContextKey]]."
  [k]
  (if (instance? ContextKey k)
    k
    (context-key* k)))

(defn get-value
  "Returns the value stored in the context for the given context key."
  [^Context context key]
  (.get context (context-key key)))

(defn ^Context assoc-value
  "Associates a value with a context, returning a new context including the
  value. Does not use nor affect the current context. Takes 2 or 3 args, where
  first arg is the context. With 2 args, the second arg is an
  [[ImplicitContextKeyedValue]] which is a value that uses a predetermined key
  for association. With 3 args, the remaining args are the key and value."
  ([^Context context implicit-context-keyed-value]
   (.with context implicit-context-keyed-value))
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

(defn ->headers
  "Returns a map to merge into the headers of an HTTP request for the purpose
  of context propagation i.e. transmit context transfer to a remote server. May
  take an optional map argument as follows:

  | key                  | description |
  |----------------------|-------------|
  |`:context`            | Context to propagate (default: current context).
  |`:text-map-propagator`| Propagator used to create headers map entries (default: propagator set in global OpenTelemetry instance)."
  ([]
   (->headers {}))
  ([{:keys [^Context context ^TextMapPropagator text-map-propagator]
     :or   {context             (current)
            text-map-propagator (otel/get-text-map-propagator)}}]
   (let [carrier (HashMap.)
         setter (reify TextMapSetter
                  (set [_ _ key value]
                    (.put carrier key value)))]
     (.inject text-map-propagator context nil setter)
     (into {} carrier))))

(defn ^Context headers->merged-context
  "Returns a context formed by extracting a propagated context from a map
  `headers` and merging with another context i.e. accept context transfer from
  a remote server. May take an option map as follows:

  | key                  | description |
  |----------------------|-------------|
  |`:context`            | Context to merge with (default: current context).
  |`:text-map-propagator`| Propagator used to extract data from the headers map (default: propagator set in global OpenTelemetry instance)."
  ([headers]
   (headers->merged-context headers {}))
  ([headers {:keys [^Context context ^TextMapPropagator text-map-propagator]
             :or   {context             (current)
                    text-map-propagator (otel/get-text-map-propagator)}}]
   (let [getter (reify TextMapGetter
                  (keys [_ _]
                    (keys headers))
                  (get [_ _ key]
                    (some-> (get headers key)
                            (str/split #",")
                            first
                            str/trim)))]
     (.extract text-map-propagator context nil getter))))

(comment

  )