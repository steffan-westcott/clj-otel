(ns steffan-westcott.clj-otel.util
  "General utility functions."
  (:import (clojure.lang IPersistentVector Named)
           (java.time Duration Instant)
           (java.util.concurrent TimeUnit)))

(defprotocol AsDuration
  (duration [d]
   "Coerce to a `Duration` instance."))

(extend-protocol AsDuration
 Duration
   (duration [d]
     d)
 IPersistentVector
   (duration [d]
     (let [[amount unit] d]
       (Duration/of amount unit))))

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
  (qualified-name [x]
   "Given a keyword or symbol, returns the name qualified with its namespace if
    it has one. The namespace and name are separated by '.' to follow the
    OpenTelemetry specification. Given any other type of argument, returns it
    as a string."))

(extend-protocol AsQualifiedName
 Named
   (qualified-name [x]
     (if-let [ns (namespace x)]
       (str ns "." (name x))
       (name x)))
 Object
   (qualified-name [x]
     (str x)))
