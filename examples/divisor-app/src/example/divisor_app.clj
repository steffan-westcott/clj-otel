(ns example.divisor-app
  (:require [org.corfield.logging4j2 :as log]
            [steffan-westcott.clj-otel.adapter.log4j :as log4j]
            [steffan-westcott.clj-otel.api.metrics.instrument :as instrument]
            [steffan-westcott.clj-otel.api.trace.span :as span]
            [steffan-westcott.clj-otel.instrumentation.runtime-telemetry-java8 :as
             runtime-telemetry]
            [steffan-westcott.clj-otel.sdk.autoconfigure :as autoconfig])
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
  "Initializes autoconfigured OpenTelemetry, registers JVM runtime metrics
   and initializes Log4j `CljOtelAppender` instances."
  []
  (autoconfig/init-otel-sdk!)
  (runtime-telemetry/register!)
  (log4j/initialize)
  (log/info "OpenTelemetry initialized")
  :initialized)



(defn -main
  "Application Uberjar entry point."
  [& _args]
  (init-otel)
  (println (gcd 18 24)))


;;;;;;;;;;;;;

(comment

  ;; Initialize OpenTelemetry SDK instance and set as default OpenTelemetry
  (init-otel)

  ;; Exercise the application
  (gcd 18 24)

  ;
)