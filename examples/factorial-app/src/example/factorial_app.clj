;!zprint {:style [:respect-nl]}

(ns example.factorial-app
  "An example application demonstrating programmatic configuration,
   initialisation and shutdown of the OpenTelemetry SDK."
  #_{:clj-kondo/ignore [:unsorted-required-namespaces]}
  (:require

    ;; Require desired span exporters
    [steffan-westcott.clj-otel.exporter.otlp.grpc.trace :as otlp-grpc-trace]
    ;[steffan-westcott.clj-otel.exporter.otlp.http.trace :as otlp-http-trace]
    ;[steffan-westcott.clj-otel.exporter.zipkin :as zipkin]
    ;[steffan-westcott.clj-otel.exporter.logging :as logging]
    ;[steffan-westcott.clj-otel.exporter.logging-otlp :as logging-otlp]

    ;; Require desired metric exporters
    [steffan-westcott.clj-otel.exporter.otlp.grpc.metrics :as otlp-grpc-metrics]
    ;[steffan-westcott.clj-otel.exporter.otlp.http.metrics :as otlp-http-metrics]
    ;[steffan-westcott.clj-otel.exporter.prometheus :as prometheus]

    [steffan-westcott.clj-otel.api.metrics.instrument :as instrument]
    [steffan-westcott.clj-otel.api.trace.span :as span]
    [steffan-westcott.clj-otel.resource.resources :as res]
    [steffan-westcott.clj-otel.sdk.otel-sdk :as sdk]
    [steffan-westcott.clj-otel.sdk.meter-provider :as meter])
  (:import (java.util.concurrent TimeUnit)))

(defonce
  ^{:doc "Delay containing a counter that records the number of factorials
          calculated."}
  factorials-count
  (delay (instrument/instrument {:name        "factorials-count"
                                 :instrument-type :counter
                                 :unit        "{factorials}"
                                 :description "The number of factorials calculated"})))



(defn init-otel!
  "Configure and initialise the OpenTelemetry SDK as the global OpenTelemetry
   instance used by the application. This function should be evaluated before
   performing any OpenTelemetry API operations such as tracing. This function
   may be evaluated once only, any attempts to evaluate it more than once will
   result in error."
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

                   ;; Export spans to locally deployed OpenTelemetry Collector via HTTP
                   ; (otlp-http-trace/span-exporter)

                   ;; Export spans to locally deployed Zipkin
                   ; (zipkin/span-exporter)

                   ;; Export spans to Honeycomb using OTLP via gRPC
                   ;(otlp-grpc-trace/span-exporter
                   ;  {:endpoint "https://api.honeycomb.io:443"
                   ;   :headers  {"x-honeycomb-team"    "YOUR_HONEYCOMB_TEAM_API_KEY"
                   ;              "x-honeycomb-dataset" "YOUR_HONEYCOMB_DATASET"}})

                   ;; Export spans to Honeycomb using OTLP via HTTP
                   ;(otlp-http-trace/span-exporter
                   ;  {:endpoint "https://api.honeycomb.io:443"
                   ;   :headers  {"x-honeycomb-team"    "YOUR_HONEYCOMB_TEAM_API_KEY"
                   ;              "x-honeycomb-dataset" "YOUR_HONEYCOMB_DATASET"}})

                   ;; Export spans to Lightstep using OTLP via gRPC
                   ;(otlp-grpc-trace/span-exporter
                   ;  {:endpoint "https://ingest.lightstep.com:443"
                   ;   :headers  {"lightstep-access-token" "YOUR_LIGHTSTEP_ACCESS_TOKEN"}})

                   ;; Export spans to java.util.logging (used for debugging
                   ;; only)
                   ;(logging/span-exporter)

                   ;; Export spans to java.util.logging in OTLP JSON format
                   ;; (used for debugging only)
                   ;(logging-otlp/span-exporter)

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
               ;                  :metric-exporter (otlp-http-metrics/metric-exporter)})}

               ;; Export metrics using in-process Prometheus HTTP server
               ;{:metric-reader (prometheus/http-server)}
              ]}}))



(defn close-otel!
  "Shut down OpenTelemetry SDK processes. This should be called before the
   application exits."
  []
  (sdk/close-otel-sdk!))



(defn factorial
  "Returns the factorial of a number."
  [n]
  (span/with-span! {:name "Computing factorial"}
    (Thread/sleep 500)
    (instrument/add! @factorials-count {:value 1})
    (->> n
         inc
         (range 1)
         (reduce *))))



(comment
  (init-otel!) ; once only
  (factorial 7)
  (close-otel!)
)
