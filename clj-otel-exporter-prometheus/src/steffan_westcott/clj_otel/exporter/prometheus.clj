(ns steffan-westcott.clj-otel.exporter.prometheus
  "Metric data exporter to Prometheus."
  (:require [steffan-westcott.clj-otel.sdk.export :as export])
  (:import (com.sun.net.httpserver Authenticator HttpHandler)
           (io.opentelemetry.exporter.prometheus PrometheusHttpServer)
           (io.opentelemetry.sdk.metrics.export DefaultAggregationSelector)
           (java.util.function Predicate)))

(defn http-server
  "Returns a Prometheus HTTP server which acts as a `MetricReader`
   implementation. May take an option map as follows:

   | key                           | description |
   |-------------------------------|-------------|
   |`:host`                        | The host to bind to (default: `\"0.0.0.0\"`).
   |`:port`                        | The port to bind to (default: `9464`).
   |`:executor`                    | `ExecutorService` to use for the Prometheus HTTP server (default: a fixed pool of 5 daemon threads).
   |`:registry`                    | `PrometheusRegistry` to use for the HTTP server (default: new registry).
   |`:label?`                      | fn which takes the name of a resource attribute and returns true if it should be added as a label on each exported metric (default: no attributes added).
   |`:default-aggregation-selector`| Function which takes an `InstrumentType` and returns default `Aggregation` (default: same as `DefaultAggregationSelector/getDefault`).\n
   |`:memory-mode`                 | Either `:immutable-data` for thread safe or `:reusable-data` for non thread safe (but reduced) data allocations (default: `:reusable-data`).
   |`:default-handler`             | Override for default `com.sun.net.httpserver.HttpHandler`.
   |`:authenticator`               | `com.sun.net.httpserver.Authenticator` to use (default: no authentication)."
  (^PrometheusHttpServer []
   (http-server {}))
  (^PrometheusHttpServer
   [{:keys [host port executor registry label? default-aggregation-selector memory-mode
            ^HttpHandler default-handler ^Authenticator authenticator]}]
   (let [builder
         (cond-> (PrometheusHttpServer/builder)
           host            (.setHost host)
           port            (.setPort port)
           executor        (.setExecutor executor)
           registry        (.setPrometheusRegistry registry)
           label?          (.setAllowedResourceAttributesFilter (reify
                                                                 Predicate
                                                                   (test [_ attr-name]
                                                                     (boolean (label? attr-name)))))
           default-aggregation-selector (.setDefaultAggregationSelector
                                         (reify
                                          DefaultAggregationSelector
                                            (getDefaultAggregation [_ instrument-type]
                                              (default-aggregation-selector instrument-type))))
           memory-mode     (.setMemoryMode (export/keyword->MemoryMode memory-mode))
           default-handler (.setDefaultHandler default-handler)
           authenticator   (.setAuthenticator authenticator))]
     (.build builder))))
