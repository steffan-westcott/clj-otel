(ns example.square-app
  "An example application demonstrating usage of the OpenTelemetry API with `clj-otel`."
  (:require [steffan-westcott.clj-otel.api.metrics.instrument :as instrument]
            [steffan-westcott.clj-otel.api.trace.span :as span]
            [steffan-westcott.clj-otel.instrumentation.runtime-telemetry-java8 :as
             runtime-telemetry]
            [steffan-westcott.clj-otel.sdk.autoconfigure :as autoconfig]))


(defonce ^{:doc "Delay containing counter that records the number of squares calculated."}
         squares-count
  (delay (instrument/instrument {:name        "app.square.squares-count"
                                 :instrument-type :counter
                                 :unit        "{squares}"
                                 :description "The number of squares calculated"})))

(defn square
  "Returns the square of a number."
  [n]
  (span/with-span! [::squaring {:app.square/n n}]
    (span/add-event! "my event")
    (instrument/add! @squares-count {:value 1})
    (* n n)))

;;;;;;;;;;;;;

(comment

  ;; Initialise OpenTelemetry SDK instance and set as default used by `clj-otel`
  (defonce _otel-sdk
    (autoconfig/init-otel-sdk!))

  ;; Optional - Add JVM metrics in export
  (defonce jvm-reg
    (runtime-telemetry/register!))

  ;; Exercise the application
  (square 9)

  ;; Remove JVM metrics in export
  (runtime-telemetry/close! jvm-reg)

  ;
)