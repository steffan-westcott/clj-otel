(ns steffan-westcott.clj-otel.util
  "General utility functions."
  (:import (clojure.lang IPersistentVector Keyword)
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
   "Given a keyword, returns the name qualified with its namespace if it has
    one. Given anything other than a keyword, returns argument."))

(extend-protocol AsQualifiedName
 Keyword
   (qualified-name [x]
     (str (symbol x)))
 Object
   (qualified-name [x]
     x))
