(ns example.auto_sdk_config
  (:require [steffan-westcott.otel.api.trace.span :as span]))

(defn init-tracer! []
  ;; Configuration for the tracer instance used by the application.
  (let [tracer (span/get-tracer {:name "example-app" :version "1.0.0"})]
    (span/set-default-tracer! tracer)))

(defn square [n]
  (span/with-span! {:name "squaring" :attributes {:my-arg n}}
    (Thread/sleep 500)
    (span/add-span-data! {:event {:name "my event"}})
    (* n n)))

;;;;;;;;;;;;;

(init-tracer!)
(square 9)

(comment
  ;; To try out this example (docker required):
  ;;
  ;; Run the following command to spin up a Zipkin server instance
  ;;
  ;; docker run --rm -p 9411:9411 openzipkin/zipkin-slim
  ;;
  ;; Start a REPL with these aliases enabled:
  ;; - otel
  ;; - traces-zipkin
  ;; - metrics-none
  ;;
  ;; Then load this namespace into the REPL to run the example.
  ;;
  ;; Open a browser at http://localhost:9411/zipkin/ and click Run Query to
  ;; view a trace with a single span.
  ;; A shutdown hook is installed to cleanly terminate OpenTelemetry SDK
  ;; processes when the JVM process exits.

  ;; ----------
  ;;
  ;; To try out the example with Jaeger with gRPC instead:
  ;;
  ;; Spin up all Jaeger components in a container with the command
  ;;
  ;; docker run --rm -it --name jaeger -p 16686:16686 -p 14250:14250 jaegertracing/all-in-one
  ;;
  ;; Start a REPL with these aliases enabled:
  ;; - otel
  ;; - traces-jaeger
  ;; - metrics-none
  ;;
  ;; Load this namespace then browse the traces at http://localhost:16686
  )
