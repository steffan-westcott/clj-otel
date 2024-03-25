(ns example.sentence-summary-service.metrics
  "Metrics instruments maintained by the service"
  (:require [steffan-westcott.clj-otel.api.metrics.instrument :as instrument]))


(def instrument-opts
  "Map of instruments and their options. All instruments in the map take
   measurements synchronously."
  {:words-count {:name        "service.sentence-summary.words-count"
                 :instrument-type :histogram
                 :unit        "{words}"
                 :description "The number of words in each sentence"}})



(defn instruments
  "Builds and returns a map of instruments for use by the service."
  []
  (update-vals instrument-opts instrument/instrument))
