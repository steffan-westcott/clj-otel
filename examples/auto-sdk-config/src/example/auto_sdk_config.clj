(ns example.auto-sdk-config
  (:require [steffan-westcott.clj-otel.api.metrics.instrument :as instrument]
            [steffan-westcott.clj-otel.api.trace.span :as span]
            [steffan-westcott.clj-otel.instrumentation.runtime-telemetry-java8 :as
             runtime-telemetry]))


(defonce ^{:doc "Counter that records the number of squares calculated."} squares-count
  (instrument/instrument {:name        "app.square.squares-count"
                          :instrument-type :counter
                          :unit        "{squares}"
                          :description "The number of squares calculated"}))

(defn square
  "Returns the square of a number."
  [n]
  (span/with-span! {:name       "squaring"
                    :attributes {:app.square/n n}}
    (Thread/sleep 500)
    (span/add-span-data! {:event {:name "my event"}})
    (instrument/add! squares-count {:value 1})
    (* n n)))

;;;;;;;;;;;;;

(defonce ^{:doc "JVM metrics registration"} _jvm-reg
  (runtime-telemetry/register!))

(square 9)

