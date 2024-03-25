(ns example.average-service.metrics
  "Metrics instruments maintained by the service"
  (:require [steffan-westcott.clj-otel.api.metrics.instrument :as instrument]))


(def instrument-opts
  "Map of instruments and their options. All instruments in the map take
   measurements synchronously."
  {:average-result {:name        "service.average.average-result"
                    :instrument-type :histogram
                    :description "The resulting averages"}})



(defn instruments
  "Builds and returns a map of instruments for use by the service."
  []
  (update-vals instrument-opts instrument/instrument))
