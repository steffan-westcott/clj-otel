(ns example.planet-service.metrics
  "Metrics instruments maintained by the service"
  (:require [steffan-westcott.clj-otel.api.metrics.instrument :as instrument]))


(def instrument-opts
  "Map of instruments and their options. All instruments in the map take
   measurements synchronously."
  {:statistic-lookups {:name        "service.planet.statistic-lookups"
                       :instrument-type :counter
                       :unit        "{lookups}"
                       :description "The number of statistic lookups"}})



(defn instruments
  "Builds and returns a map of instruments for use by the service."
  []
  (update-vals instrument-opts instrument/instrument))
