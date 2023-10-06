(ns example.cube-app
  "An example application demonstrating usage of the OpenTelemetry API with `clj-otel`."
  (:require [steffan-westcott.clj-otel.api.metrics.instrument :as instrument]
            [steffan-westcott.clj-otel.api.trace.span :as span]))


(defonce ^{:doc "Delay containing counter that records the number of cubes calculated."} cubes-count
  (delay (instrument/instrument {:name        "app.cube.cubes-count"
                                 :instrument-type :counter
                                 :unit        "{cubes}"
                                 :description "The number of cubes calculated"})))

(defn cube
  "Returns the cube of a number."
  [n]
  (span/with-span! [::cubing {:app.cube/n n}]
    (span/add-event! "my event")
    (instrument/add! @cubes-count {:value 1})
    (* n n n)))

;;;;;;;;;;;;;

(comment

  ;; Exercise the application
  (cube 5)

  ;
)

