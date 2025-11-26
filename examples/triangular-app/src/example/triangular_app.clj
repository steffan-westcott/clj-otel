(ns example.triangular-app
  "An example application demonstrating usage of the OpenTelemetry API with `clj-otel`."
  (:require [example.common.slf4j.utils :as log]
            [steffan-westcott.clj-otel.adapter.logback :as logback]
            [steffan-westcott.clj-otel.api.metrics.instrument :as instrument]
            [steffan-westcott.clj-otel.api.trace.span :as span]
            [steffan-westcott.clj-otel.instrumentation.runtime-telemetry-java8 :as
             runtime-telemetry]
            [steffan-westcott.clj-otel.sdk.autoconfigure :as autoconfig])
  (:gen-class))


(defonce ^{:doc "Delay containing counter that records the sum of triangular numbers calculated."}
         triangular-sum
  (delay (instrument/instrument {:name        "app.triangular.triangular-sum"
                                 :instrument-type :counter
                                 :unit        "{triangular}"
                                 :description "The sum of triangular numbers calculated"})))


(defn triangular
  "Returns the nth triangular number."
  [n]
  (span/with-span! [::calculating-triangular {:app.triangular/n n}]
    (log/debug "About to calculate triangular number")
    (let [t (/ (* (inc n) n) 2)]
      (instrument/add! @triangular-sum {:value t})
      (log/debug {"input"  n
                  "output" t}
                 "Computed triangular number")
      t)))


(defn init-otel
  "Initializes autoconfigured OpenTelemetry, registers JVM runtime metrics
   and initializes Log4j `CljOtelAppender` instances."
  []
  (autoconfig/init-otel-sdk!)
  (runtime-telemetry/register!)
  (logback/initialize)
  (log/install-bridge-handler)
  (log/info "OpenTelemetry initialized")
  :initialized)



(defn -main
  "Application uberjar entry point."
  [& _args]
  (init-otel)
  (println (triangular 7)))


;;;;;;;;;;;;;

(comment

  ;; Initialize OpenTelemetry SDK instance and set as default OpenTelemetry
  (init-otel)

  ;; Exercise the application
  (triangular 5)

  ;
)
