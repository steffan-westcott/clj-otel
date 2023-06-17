(ns steffan-westcott.clj-otel.util
  "General utility functions."
  (:require [camel-snake-kebab.core :as csk])
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
  (qualified-name [x]
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
