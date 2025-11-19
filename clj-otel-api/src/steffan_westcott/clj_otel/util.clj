(ns steffan-westcott.clj-otel.util
  "General utility functions."
  (:require [camel-snake-kebab.core :as csk])
  (:import (clojure.lang IPersistentVector Named)
           (java.time Duration Instant)
           (java.util.concurrent CompletionException ExecutionException TimeUnit)
           (java.util.function BiConsumer BiFunction Function Predicate Supplier)
           (java.util.stream Stream)))

(defprotocol AsDuration
  (duration ^Duration [d]
   "Coerce to a `Duration` instance."))

(extend-protocol AsDuration
 Duration
   (duration [d]
     d)
 IPersistentVector
   (duration [[amount ^TimeUnit unit]]
     (Duration/ofNanos (.toNanos unit amount))))

(defprotocol AsTimestamp
  (timestamp [t]
   "Coerce `Instant` to a vector `[amount ^TimeUnit unit]`."))

(extend-protocol AsTimestamp
 Instant
   (timestamp [t]
     (let [seconds-part (.getEpochSecond t)
           nanos-part   (.getNano t)
           nanos        (+ (.toNanos TimeUnit/SECONDS seconds-part) nanos-part)]
       [nanos TimeUnit/NANOSECONDS]))
 IPersistentVector
   (timestamp [t]
     t))

(defprotocol AsQualifiedName
  (qualified-name ^String [x]
   "Given a keyword or symbol, returns the name converted to follow
    OpenTelemetry conventions for attribute names; the name is converted to a
    snake_case string, where namespace and name are separated by `.`. Given any
    other type of argument, returns it as a snake_case string."))

(extend-protocol AsQualifiedName
 Named
   (qualified-name [x]
     (let [s (csk/->snake_case_string x)]
       (if-let [ns (namespace x)]
         (str (csk/->snake_case_string ns) "." s)
         s)))
 Object
   (qualified-name [x]
     (csk/->snake_case_string (str x))))

(def ^:private third-element
  (reify
   Function
     (apply [_ stream]
       (-> ^Stream stream
           (.skip 2)
           .findFirst
           (.orElse nil)))))

(defmacro compile-if
  "Evals `test` at macro expansion time and expands to `true-expr`
   or `false-expr` based on the result."
  [test true-expr false-expr]
  (if (try
        (eval test)
        (catch Throwable _
          false))
    `(do
       ~true-expr)
    `(do
       ~false-expr)))

(defmacro compile-when
  "Evals `test` at macro expansion time and expands to `true-expr`
   or nil based on the result."
  [test true-expr]
  `(compile-if ~test ~true-expr nil))

(def ^{:arglists '([^String class-name])} resolve-class
  "Returns the class with the given fully qualified name, or nil if not
   available on the current class loader."
  (memoize (fn [^String class-name]
             (try
               (Class/forName class-name)
               (catch Exception _)))))

(defn class-exists?
  "Returns true if the class with the given fully qualified name is available
   on the current class loader."
  [^String class-name]
  (boolean (resolve-class class-name)))

(defn has-method?
  "Returns true if the method with the given name and parameter types is
   available on the class."
  [^Class class ^String method-name & param-types]
  (boolean (try
             (.getMethod class method-name param-types)
             (catch Exception _))))

(defn resolve-instance?
  "Returns true if `x` is an instance of class with given name. This fn is safe
   to use with names of classes that may not be available on the class path."
  [^String class-name x]
  (boolean (some-> (resolve-class class-name)
                   (instance? x))))

(def ^{:arglists '([^Class class ^String field-name])} accessible-field
  "Returns `java.lang.reflect.Field` with accessibility flag set to true for
   class field with given name, or nil if field not found."
  (memoize (fn [^Class class ^String field-name]
             (try
               (doto (.getDeclaredField class field-name)
                 (.setAccessible true))
               (catch Exception _)))))

(defn fn-name
  "Returns the name of the currently executing function, using the StackWalker
   API first available in Java 9. If `StackWalker` is not available, returns
   nil; `(doto (Throwable.) .fillInStackTrace)` is not used to examine the
   stack, as that has poor performance with deep stacks."
  []
  (compile-if (class-exists? "java.lang.StackWalker")
              (some-> ^java.lang.StackWalker$StackFrame
                      (.walk (java.lang.StackWalker/getInstance) third-element)
                      .getClassName
                      Compiler/demunge)
              nil))

(defn supplier
  "Returns a `Supplier` implementation for function `f` that takes no args."
  ^Supplier [f]
  (reify
   Supplier
     (get [_]
       (f))))

(defn function
  "Returns a `Function` implementation for function `f` that takes one arg."
  ^Function [f]
  (reify
   Function
     (apply [_ x]
       (f x))))

(defn bifunction
  "Returns a `BiFunction` implementation for function `f` that takes 2 args."
  ^BiFunction [f]
  (reify
   BiFunction
     (apply [_ x y]
       (f x y))))

(defn biconsumer
  "Returns a `BiConsumer` implementation for function `f` that takes 2 args
   and the result is discarded."
  ^BiConsumer [f]
  (reify
   BiConsumer
     (accept [_ x y]
       (f x y))))

(defn predicate
  "Returns a `Predicate` implementation for function `pred` that takes 1 arg."
  ^Predicate [pred]
  (reify
   Predicate
     (test [_ x]
       (boolean (pred x)))))

(defn unwrap-cf-exception
  "Unwraps an exception thrown from a `CompletableFuture`."
  [e]
  (if (or (instance? ExecutionException e) (instance? CompletionException e))
    (or (ex-cause e) e)
    e))

(defn into!
  "Same as `into`, but for transient `to` collection."
  ([to from] (reduce conj! to from))
  ([to xf from] (transduce xf conj! to from)))

