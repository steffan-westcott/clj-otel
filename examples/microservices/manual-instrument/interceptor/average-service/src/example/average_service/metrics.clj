(ns example.average-service.metrics
  "Metrics instruments maintained by the service"
  (:require [steffan-westcott.clj-otel.api.metrics.instrument :as instrument]))


(def instrument-opts
  "Map of instruments and their options. All instruments in the map take
   measurements synchronously."
  {:average-result {:name "service.average.average-result"
                    :instrument-type :histogram
                    :measurement-type :double
                    :explicit-bucket-boundaries-advice [1.0 2.0 3.0 4.0 5.0 6.0 7.0 8.0 9.0 10.0
                                                        12.0 14.0 16.0 18.0 20.0 30.0 40.0]
                    :description "The resulting averages"}})



(defn instruments
  "Builds and returns a map of instruments for use by the service."
  []
  (update-vals instrument-opts instrument/instrument))
