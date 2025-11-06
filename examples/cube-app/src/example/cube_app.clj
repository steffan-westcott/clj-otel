(ns example.cube-app
  "An example application demonstrating usage of the OpenTelemetry API with `clj-otel`."
  (:require [org.corfield.logging4j2 :as log]
            [steffan-westcott.clj-otel.adapter.log4j :as log4j]
            [steffan-westcott.clj-otel.api.metrics.instrument :as instrument]
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
    (log/debug "About to calculate cube")
    (instrument/add! @cubes-count {:value 1})
    (* n n n)))

;;;;;;;;;;;;;

(comment

  ;; Initialize Log4j appender instances
  (log4j/initialize)

  ;; Exercise the application
  (cube 5)

  ;
)
