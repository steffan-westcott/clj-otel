(ns example.solar-system-service.metrics
  "Metrics instruments maintained by the service"
  (:require [steffan-westcott.clj-otel.api.metrics.instrument :as instrument]))


(def instrument-opts
  "Map of instruments and their options. All instruments in the map take
   measurements synchronously."
  {:report-count {:name        "service.solar-system.planet-report-count"
                  :instrument-type :counter
                  :unit        "{reports}"
                  :description "The number of reports built"}})



(defn instruments
  "Builds and returns a map of instruments for use by the service."
  []
  (update-vals instrument-opts instrument/instrument))
