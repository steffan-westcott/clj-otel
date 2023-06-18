(ns steffan-westcott.clj-otel.exporter.prometheus
  "Metric data exporter to Prometheus."
  (:import (io.opentelemetry.exporter.prometheus PrometheusHttpServer)))

(defn http-server
  "Returns a Prometheus HTTP server which acts as a `MetricReader`
   implementation. May take an option map as follows:

   | key       | description |
   |-----------|-------------|
   |`:host`    | The host to bind to (default: `\"0.0.0.0\"`).
   |`:port`    | The port to bind to (default: `9464`).
   |`:executor`| `ExecutorService` to use for the Prometheus HTTP server (default: a fixed pool of 5 daemon threads)."
  ([]
   (http-server {}))
  ([{:keys [host port executor]}]
   (let [builder (cond-> (PrometheusHttpServer/builder)
                   host     (.setHost host)
                   port     (.setPort port)
                   executor (.setExecutor executor))]
     (.build builder))))
