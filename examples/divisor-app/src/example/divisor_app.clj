(ns example.divisor-app
  (:require [org.corfield.logging4j2 :as log]
            [steffan-westcott.clj-otel.api.metrics.instrument :as instrument]
            [steffan-westcott.clj-otel.api.trace.span :as span]
            [steffan-westcott.clj-otel.instrumentation.runtime-telemetry-java8 :as
             runtime-telemetry]
            [steffan-westcott.clj-otel.sdk.autoconfigure :as autoconfig])
  (:import (io.opentelemetry.instrumentation.log4j.appender.v2_17 OpenTelemetryAppender))
  (:gen-class))


(defonce
  ^{:doc "Delay containing counter that records the number of greatest common divisors calculated."}
  gcd-count
  (delay (instrument/instrument {:name "app.divisor.gcd-count"
                                 :instrument-type :counter
                                 :unit "{greatest common divisors}"
                                 :description
                                 "The number of greatest common divisors calculated"})))


(defn- gcd*
  [x y]
  (if (zero? x)
    y
    (recur (mod y x) x)))



(defn gcd
  "Returns the greatest common divisor of x and y."
  [x y]
  (span/with-span! [::calculating-gcd {:app.divisor/args [x y]}]
    (log/debug {:message "About to calculate GCD"
                :x       x
                :y       y})
    (span/add-event! "my event")
    (instrument/add! @gcd-count {:value 1})
    (gcd* x y)))



(defn init-otel
  "Initialises autoconfigured OpenTelemetry and registers JVM runtime metrics."
  []
  (let [sdk (autoconfig/init-otel-sdk!)]
    (OpenTelemetryAppender/install sdk))
  (runtime-telemetry/register!)
  (log/info "OpenTelemetry initialised")
  :initialised)



(defn -main
  "Application Uberjar entry point."
  [& _args]
  (init-otel)
  (println (gcd 18 24)))


;;;;;;;;;;;;;

(comment

  ;; Initialise OpenTelemetry SDK instance and set as default used by `clj-otel`
  (init-otel)

  ;; Exercise the application
  (gcd 18 24)

  ;
)