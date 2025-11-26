(ns example.permutations-app
  "An example application demonstrating usage of the OpenTelemetry API with `clj-otel`."
  (:require [clojure.math.combinatorics :as comb]
            [example.common.slf4j.utils :as log]
            [steffan-westcott.clj-otel.adapter.logback :as logback]
            [steffan-westcott.clj-otel.api.metrics.instrument :as instrument]
            [steffan-westcott.clj-otel.api.trace.span :as span]))


(defonce ^{:doc "Delay containing counter that records the number of permutations calculated."}
         permutations-count
  (delay (instrument/instrument {:name        "app.permutations.permutations-count"
                                 :instrument-type :counter
                                 :unit        "{permutations}"
                                 :description "The number of permutations calculated"})))


(defn permutations
  "Returns the permutations of a given coll."
  [coll]
  (span/with-span! "Calculating permutations"
    (log/debug "About to generate permutations of {}" (str coll))
    (let [perms (comb/permutations coll)]
      (instrument/add! @permutations-count {:value (count perms)})
      (log/debug {"coll"        coll
                  "perms.count" (count perms)}
                 "Computed permutations")
      perms)))

;;;;;;;;;;;;;

(comment

  ;; Initialize Logback appender instances
  (logback/initialize)

  ;; Redirect java.util.logging log records to SLF4J
  (log/install-bridge-handler)

  ;; Exercise the application
  (permutations [0 0 1 2])

  ;
)
