(ns example.square-app
  "An example application demonstrating usage of the OpenTelemetry API with `clj-otel`."
  (:require [org.corfield.logging4j2 :as log]
            [steffan-westcott.clj-otel.api.metrics.instrument :as instrument]
            [steffan-westcott.clj-otel.api.trace.span :as span]
            [steffan-westcott.clj-otel.instrumentation.runtime-telemetry-java8 :as
             runtime-telemetry]
            [steffan-westcott.clj-otel.sdk.autoconfigure :as autoconfig])
  (:import (io.opentelemetry.instrumentation.log4j.appender.v2_17 OpenTelemetryAppender)))


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
    (log/info "About to calculate square number")
    (span/add-event! "my event")
    (instrument/add! @squares-count {:value 1})
    (* n n)))



(defn init-otel
  "Initialises autoconfigured OpenTelemetry and registers JVM runtime metrics."
  []
  (let [sdk (autoconfig/init-otel-sdk!)]
    (OpenTelemetryAppender/install sdk))
  (runtime-telemetry/register!)
  (log/info "OpenTelemetry initialised")
  :initialised)


;;;;;;;;;;;;;

(comment

  ;; Initialise OpenTelemetry SDK instance and set as default used by `clj-otel`
  (init-otel)

  ;; Exercise the application
  (square 9)

  ;
)