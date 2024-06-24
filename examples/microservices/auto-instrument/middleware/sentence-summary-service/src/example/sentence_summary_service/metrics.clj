(ns example.sentence-summary-service.metrics
  "Metrics instruments maintained by the service"
  (:require [steffan-westcott.clj-otel.api.metrics.instrument :as instrument]))


(def instrument-opts
  "Map of instruments and their options. All instruments in the map take
   measurements synchronously."
  {:sentence-length {:name        "service.sentence-summary.sentence-length"
                     :instrument-type :histogram
                     :unit        "{words}"
                     :description "The number of words in each sentence"
                     :explicit-bucket-boundaries-advice [0 1 2 3 4 5 6 8 10 12 15 20 30 50]}})



(defn instruments
  "Builds and returns a map of instruments for use by the service."
  []
  (update-vals instrument-opts instrument/instrument))
