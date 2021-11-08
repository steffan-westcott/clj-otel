(ns steffan-westcott.otel.util
  "General utility functions."
  (:import (java.time Duration Instant)
           (java.util.concurrent TimeUnit)))

(defn duration
  "Coerce to a [[Duration]] instance."
  [d]
  (cond
    (instance? Duration d) d
    (vector? d) (let [[amount unit] d]
                  (Duration/of amount unit))))

(defn timestamp
  "Coerce [[Instant]] to a vector `[amount ^TimeUnit unit]`."
  [t]
  (cond
    (vector? t) t
    (instance? Instant t) (let [seconds-part (.getEpochSecond ^Instant t)
                                nanos-part (.getNano ^Instant t)
                                nanos (+ (.toNanos TimeUnit/SECONDS seconds-part) nanos-part)]
                            [nanos TimeUnit/NANOSECONDS])))

(defn qualified-name
  "Given a keyword, returns the name qualified with its namespace if it has
  one. Given anything other than a keyword, returns argument."
  [x]
  (if (keyword? x)
    (str (symbol x))
    x))
