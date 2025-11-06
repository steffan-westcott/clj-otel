;!zprint {:style [:respect-nl]}

(ns example.factorial-app
  "An example application demonstrating programmatic configuration,
   initialization and shutdown of the OpenTelemetry SDK."
  (:require
    [org.corfield.logging4j2 :as log]
    [steffan-westcott.clj-otel.adapter.log4j :as log4j]
    [steffan-westcott.clj-otel.api.metrics.instrument :as instrument]
    [steffan-westcott.clj-otel.api.trace.span :as span]
    [steffan-westcott.clj-otel.exporter.otlp.grpc.logs :as otlp-grpc-logs]
    [steffan-westcott.clj-otel.exporter.otlp.grpc.metrics :as otlp-grpc-metrics]
    ;[steffan-westcott.clj-otel.exporter.otlp.http.logs :as otlp-http-logs]
    ;[steffan-westcott.clj-otel.exporter.otlp.http.metrics :as otlp-http-metrics]
    ;[steffan-westcott.clj-otel.exporter.otlp.http.trace :as otlp-http-trace]
    [steffan-westcott.clj-otel.exporter.otlp.grpc.trace :as otlp-grpc-trace]
    [steffan-westcott.clj-otel.resource.resources :as res]
    [steffan-westcott.clj-otel.sdk.meter-provider :as meter]
    [steffan-westcott.clj-otel.sdk.otel-sdk :as sdk])
  (:import (java.util.concurrent TimeUnit))
  (:gen-class))

(defonce
  ^{:doc "Delay containing a counter that records the number of factorials
          calculated."}
  factorials-count
  (delay (instrument/instrument {:name        "factorials-count"
                                 :instrument-type :counter
                                 :unit        "{factorials}"
                                 :description "The number of factorials calculated"})))


(defn init-otel!
  "Configure and initialize the OpenTelemetry SDK as the default OpenTelemetry
   instance used by the application. This function should be evaluated before
   performing any OpenTelemetry API operations such as tracing. This function
   may be evaluated once only, any attempts to evaluate it more than once will
   result in error. A shutdown hook is registered that close the OpenTelemetry
   instance when the application closes."
  []
  (sdk/init-otel-sdk!

   ;; The service name is the minimum resource information.
   "factorial-app"

   {;; The collection of additional resources are merged with the service name
    ;; to form information about the entity for which telemetry is recorded.
    ;; Here the additional resources provide information on the host, OS,
    ;; process and JVM.
    :resources [(res/host-resource)
                (res/os-resource)
                (res/process-resource)
                (res/process-runtime-resource)]

    ;; Configuration options for the sampling, batching and export of traces.
    :tracer-provider
    {:span-processors

     ;; Configure selected span exporter(s). See span exporter docstrings for
     ;; further configuration options.
     [{:exporters [
                   ;; Export spans to locally deployed OpenTelemetry Collector via gRPC
                   (otlp-grpc-trace/span-exporter)

                   ;; Export spans to locally deployed OpenTelemetry Collector via
                   ;; HTTP
                   ;(otlp-http-trace/span-exporter)

                  ]}]}

    ;; Configuration options for transformation, aggregation and export of metrics.
    :meter-provider
    {:readers [
               ;; Export metrics once every 10 seconds to locally deployed
               ;; OpenTelemetry Collector via gRPC
               {:metric-reader (meter/periodic-metric-reader
                                {:interval        [10 TimeUnit/SECONDS]
                                 :metric-exporter (otlp-grpc-metrics/metric-exporter)})}

               ;; Export metrics once every 10 seconds to locally deployed
               ;; OpenTelemetry Collector via HTTP
               ;{:metric-reader (meter/periodic-metric-reader
               ;                 {:interval        [10 TimeUnit/SECONDS]
               ;                  :metric-exporter
               ;                  (otlp-http-metrics/metric-exporter)})}
              ]}

    ;; Configuration options for batching and export of log records.
    :logger-provider {:log-record-processors
                      [{:exporters
                        [
                         ;; Export log records to locally deployed OpenTelemetry
                         ;; Collector via gRPC
                         (otlp-grpc-logs/log-record-exporter)]}]}})

  ;; Initialize CljOtelAppender instances
  (log4j/initialize)

  (log/info "OpenTelemetry initialized")
  :initalized)



(defn factorial
  "Returns the factorial of a number."
  [n]
  (span/with-span! "Computing factorial"
    (instrument/add! @factorials-count {:value 1})
    (log/debug "About to compute factorial")
    (->> n
         inc
         (range 1)
         (reduce *))))



(defn -main
  "Application Uberjar/native entry point. Programmatically configures
   OpenTelemetry and exercises the application."
  [& _args]
  (init-otel!)
  (println (factorial 5)))



(comment

  ;; Initialise OpenTelemetry SDK
  (init-otel!)

  ;; Exercise the application
  (factorial 7)

  ;
)
