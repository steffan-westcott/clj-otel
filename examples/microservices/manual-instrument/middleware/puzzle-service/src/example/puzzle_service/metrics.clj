(ns example.puzzle-service.metrics
  "Metrics instruments maintained by the service"
  (:require [steffan-westcott.clj-otel.api.metrics.instrument :as instrument]))


(def instrument-opts
  "Map of instruments and their options. All instruments in the map take
   measurements synchronously."
  {:puzzle-size-letters {:name        "service.puzzle.puzzle-size-letters"
                         :instrument-type :histogram
                         :unit        "{letters}"
                         :explicit-bucket-boundaries-advice [4 8 12 16 20 24 28 32 36 40 48 64]
                         :description "The number of letters in each generated puzzle"}})



(defn instruments
  "Builds and returns a map of instruments for use by the service."
  []
  (update-vals instrument-opts instrument/instrument))
