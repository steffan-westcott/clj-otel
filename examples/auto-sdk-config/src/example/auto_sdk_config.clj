(ns example.auto_sdk_config
  (:require [steffan-westcott.otel.api.trace.span :as span]))

(defn init-tracer!
  "Set default tracer used when manually creating spans."
  []
  (let [tracer (span/get-tracer)]
    (span/set-default-tracer! tracer)))

(defn square
  "Returns the square of a number."
  [n]
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
  ;; Run the following command to spin up containers for Jaeger, Zipkin and
  ;; OpenTelemetry Collector:
  ;;
  ;; cd examples
  ;; docker-compose up -d
  ;;
  ;; Then load this namespace into the REPL to run the example.
  ;;
  ;; Open a browser at http://localhost:9411/zipkin/ and click Run Query to
  ;; view a trace with a single span.
  ;;
  ;; A shutdown hook is installed to cleanly terminate OpenTelemetry SDK
  ;; processes when the JVM process exits.
  ;;
  ;; To stop and remove the containers:
  ;;
  ;; cd examples
  ;; docker-compose down -v

  )
