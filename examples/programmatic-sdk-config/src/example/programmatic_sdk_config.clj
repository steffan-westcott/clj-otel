(ns example.programmatic_sdk_config
  "An example application demonstrating programmatic configuration,
  initialisation and shutdown of the OpenTelemetry SDK."
  #_{:clj-kondo/ignore [:unsorted-required-namespaces]}
  (:require

    ;; Require desired span exporters
    [steffan-westcott.otel.exporter.jaeger-grpc :as jaeger-grpc]
    ;[steffan-westcott.otel.exporter.jaeger-thrift :as jaeger-thrift]
    ;[steffan-westcott.otel.exporter.logging :as logging]
    ;[steffan-westcott.otel.exporter.logging-otlp :as logging-otlp]
    ;[steffan-westcott.otel.exporter.otlp-grpc-trace :as otlp-grpc-trace]
    ;[steffan-westcott.otel.exporter.otlp-http-trace :as otlp-http-trace]
    ;[steffan-westcott.otel.exporter.zipkin :as zipkin]

    [steffan-westcott.otel.api.trace.span :as span]
    [steffan-westcott.otel.resource.resources :as res]
    [steffan-westcott.otel.sdk.otel-sdk :as sdk])

  (:import (io.opentelemetry.semconv.resource.attributes ResourceAttributes)))

(defn init-otel!
  "Configure and initialise the OpenTelemetry SDK as the global OpenTelemetry
  instance used by the application. This should be called before performing
  any OpenTelemetry API operations such as tracing."
  []
  (sdk/init-otel-sdk!
    {
     ;; The collection of resources are merged to form information about the
     ;; entity for which telemetry is recorded.
     :resources [
                 ;; A minimal resource which identifies this application
                 {:attributes {ResourceAttributes/SERVICE_NAME "example-app"}}

                 ;; More resources which provide information on the host, OS, process and JVM
                 (res/host-resource)
                 (res/os-resource)
                 (res/process-resource)
                 (res/process-runtime-resource)]

     ;; Configuration options for the context propagation, sampling, batching
     ;; and export of traces. Here we configure export to a local Jaeger server
     ;; with default options.
     :tracer-provider-opts
     {:span-processors
      [{:exporters [
                    ;; Configure selected span exporter(s). See span exporter
                    ;; docstrings for further configuration options.

                    ;; Export spans to locally deployed Jaeger via gRPC
                    (jaeger-grpc/span-exporter)

                    ;; Export spans to locally deployed Jaeger via Thrift
                    ; (jaeger-thrift/span-exporter)

                    ;; Export spans to locally deployed Zipkin
                    ; (zipkin/span-exporter)

                    ;; Export spans to locally deployed OpenTelemetry Collector
                    ;; via gRPC
                    ; (otlp-grpc-trace/span-exporter)

                    ;; Export spans to locally deployed OpenTelemetry Collector
                    ;; via HTTP
                    ; (otlp-http-trace/span-exporter)

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

                    ]}]}}))

(defn close-otel!
  "Shut down OpenTelemetry SDK processes. This should be called before the
  application exits."
  []
  (sdk/close-otel-sdk!))

(defn square
  "Returns the square of a number."
  [n]
  (span/with-span! {:name "squaring"}
    (Thread/sleep 500)
    (* n n)))

(comment
  ;; To try out this example (docker required):
  ;;
  ;; Run the following command to spin up containers for Jaeger, Zipkin and
  ;; OpenTelemetry Collector:
  ;;
  ;; cd examples
  ;; docker-compose up -d
  ;;
  ;; then evaluate the following forms in a REPL
  ;;
  (init-otel!)
  (square 7)

  ;; Open a browser at http://localhost:9411/zipkin/ and click Run Query to
  ;; view a trace with a single span.
  ;;
  ;; Finally, evaluate this form to shut down the OpenTelemetry SDK
  ;;
  (close-otel!)

  ;; To stop and remove the containers:
  ;;
  ;; cd examples
  ;; docker-compose down -v
  )
