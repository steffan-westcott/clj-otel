(ns example.sum-service.metrics
  "Metrics instruments maintained by the service"
  (:require [steffan-westcott.clj-otel.api.metrics.instrument :as instrument]))


(def instrument-opts
  "Map of instruments and their options. All instruments in the map take
   measurements synchronously."
  {:sum-result {:name "service.sum.sum-result"
                :instrument-type :histogram
                :explicit-bucket-boundaries-advice [5 10 15 20 25 30 35 40 50 60 80 100]
                :description "The resulting sum value"}})



(defn instruments
  "Builds and returns a map of instruments for use by the service."
  []
  (update-vals instrument-opts instrument/instrument))
