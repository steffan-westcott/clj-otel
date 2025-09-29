(ns steffan-westcott.clj-otel.context
  "Functions for working with `io.opentelemetry.context.Context` objects."
  (:require [clojure.string :as str]
            [steffan-westcott.clj-otel.api.otel :as otel])
  (:import (io.opentelemetry.context Context ContextKey ImplicitContextKeyed Scope)
           (io.opentelemetry.context.propagation TextMapGetter TextMapPropagator TextMapSetter)
           (java.util HashMap Map)))

(def ^:dynamic ^:private *bound-context*
  nil)

(defn root
  "Returns the root context that all other contexts are derived from."
  ^Context []
  (Context/root))

(defn current
  "Returns the current context, which is stored in a thread local `Context`
   object. If no such context exists, the root context is returned instead."
  ^Context []
  (Context/current))

(defn set-current!
  "Sets the current context. The returned `Scope` must be closed to prevent
   broken traces and memory leaks. [[with-context!]] and [[with-value!]] can be
   used instead of this function to ensure the scope is closed when leaving a
   lexical boundary i.e. a body of forms."
  ^Scope [^Context context]
  (.makeCurrent context))

(defn close-scope!
  "Closes the given scope."
  [^Scope scope]
  (.close scope))

(defn dyn
  "Returns the bound context, which is stored in a Clojure dynamic var. If no
   context is bound, the current context or root context is returned instead."
  ^Context []
  (or *bound-context* (current)))

(defmacro bind-context!
  "Sets the bound context to be the provided `context`, then evaluates `body`.
   The original bound context is restored after body evaluation completes."
  [context & body]
  `(binding [*bound-context* ~context]
     ~@body))

(defmacro with-context!
  "Sets the current and bound context to be the provided `context`, then
   evaluates `body`. The original contexts are restored after body evaluation
   completes."
  [context & body]
  `(let [^Context context# ~context]
     (bind-context! context#
       (with-open [_scope# (.makeCurrent context#)]
         ~@body))))

(def ^:private context-key*
  (memoize (fn [k]
             (ContextKey/named (name k)))))

(defprotocol AsContextKey
  (context-key ^ContextKey [k]
   "Coerces k to a `ContextKey`."))

(extend-protocol AsContextKey
 ContextKey
   (context-key [k]
     k)
 Object
   (context-key [k]
     (context-key* k)))

(defn get-value
  "Returns the value stored in the context for the given context key."
  [^Context context key]
  (.get context (context-key key)))

(defn assoc-value
  "Associates a key-value with a `context`, returning a new `Context` that
   includes the key-value. Does not use nor affect the current context. Takes
   `key` and `value`, or an `ImplicitContextKeyed` instance which is a value
   that uses a predetermined key."
  (^Context [^Context context ^ImplicitContextKeyed implicit-context-keyed]
   (.with context implicit-context-keyed))
  (^Context [^Context context key value]
   (.with context (context-key key) value)))

(defmacro with-value!
  "Associates an `ImplicitContextKeyed` instance `implicit-context-keyed` with
   the bound or current context, sets this as the current and bound context,
   then evaluates `body`. The original contexts are restored after body
   evaluation completes."
  [implicit-context-keyed & body]
  `(with-context! (assoc-value (dyn) ~implicit-context-keyed)
     ~@body))

(defmacro with-bound-context!
  "Sets the current context to the bound context, then evaluates `body`. The
   original current context is restored after body evaluation completes."
  [& body]
  `(let [^Context context# (dyn)]
     (with-open [_scope# (.makeCurrent context#)]
       ~@body)))

(defn current-context-interceptor
  "Returns a Pedestal interceptor that will on entry set the current context to
   the value that has key `context-key` in the interceptor context map. The
   original value of current context is restored on evaluation on interceptor
   exit (either `leave` or `error`). The `Scope` of the set context is stored
   in the interceptor context map with key `scope-key`."
  [context-key scope-key]
  {:name  ::current-context
   :enter (fn enter [ctx]
            (let [scope (set-current! (get ctx context-key))]
              (assoc ctx scope-key scope)))
   :leave (fn leave [ctx]
            (close-scope! (get ctx scope-key))
            ctx)
   :error (fn error [ctx e]
            (close-scope! (get ctx scope-key))
            (assoc ctx :io.pedestal.interceptor.chain/error e))})

(defn bound-context-interceptor
  "Returns a Pedestal interceptor that will set the bound context to the value
   that has key `context-key` in the interceptor context map. The bound context
   (a dynamic var) is set for subsequent interceptors in the chain; the bound
   context is unset when this interceptor exits (either `leave` or `error`)."
  [context-key]
  {:name  ::bound-context
   :enter (fn enter [ctx]
            (update ctx :bindings assoc #'*bound-context* (get ctx context-key)))
   :leave (fn leave [ctx]
            (update ctx :bindings dissoc #'*bound-context*))})

(def ^:private map-setter
  (delay (reify
          TextMapSetter
            (set [_ carrier key value]
              (.put ^Map carrier key value)))))

(def ^:private map-getter
  (delay (reify
          TextMapGetter
            (keys [_ carrier]
              (keys carrier))
            (get [_ carrier key]
              (some-> (get carrier (str/lower-case key))
                      (str/split #",")
                      first
                      str/trim)))))

(defn ->headers
  "Returns a map to merge into the headers of an HTTP request for the purpose
   of context propagation i.e. transfer context to a remote server. May take an
   optional map argument as follows:

   | key                  | description |
   |----------------------|-------------|
   |`:context`            | Context to propagate (default: bound or current context).
   |`:text-map-propagator`| Propagator used to create headers map entries (default: propagator set in default or global `OpenTelemetry` instance).
   |`:setter`             | ^TextMapSetter used to build contents of returned value (default: uses `java.util.Map/put` directly)."
  ([]
   (->headers {}))
  ([{:keys [^Context context ^TextMapPropagator text-map-propagator setter]
     :or   {context (dyn)
            text-map-propagator (otel/get-text-map-propagator)
            setter  @map-setter}}]
   (let [carrier (HashMap.)]
     (.inject text-map-propagator context carrier setter)
     (into {} carrier))))

(defn headers->merged-context
  "Returns a context formed by extracting a propagated context from a map
   `headers` and merging with another context i.e. accept context transfer from
   a remote server. May take an option map as follows:

   | key                  | description |
   |----------------------|-------------|
   |`:context`            | Context to merge with (default: bound or current context).
   |`:text-map-propagator`| Propagator used to extract data from the headers map (default: propagator set in default or global `OpenTelemetry` instance).
   |`:getter`             | ^TextMapGetter used to get contents of `headers` (default: lower-cases key before lookup in `headers`; suitable for `headers` map in a Ring request)."
  (^Context [headers]
   (headers->merged-context headers {}))
  (^Context
   [headers
    {:keys [^Context context ^TextMapPropagator text-map-propagator getter]
     :or   {context (dyn)
            text-map-propagator (otel/get-text-map-propagator)
            getter  @map-getter}}]
   (.extract text-map-propagator context headers getter)))
