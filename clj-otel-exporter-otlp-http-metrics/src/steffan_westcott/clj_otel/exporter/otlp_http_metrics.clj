(ns steffan-westcott.clj-otel.exporter.otlp-http-metrics
  "Metric data exporter using OpenTelemetry Protocol via HTTP."
  (:require [steffan-westcott.clj-otel.util :as util])
  (:import (io.opentelemetry.exporter.otlp.http.metrics OtlpHttpMetricExporter OtlpHttpMetricExporterBuilder)))

(defn- ^OtlpHttpMetricExporterBuilder add-headers [builder headers]
  (reduce-kv #(.addHeader ^OtlpHttpMetricExporterBuilder %1 %2 %3) builder headers))

(defn metric-exporter
  "Returns a metric data exporter that sends span data using OTLP via HTTP,
  using OpenTelemetry's protobuf model. May take an option map as follows:

  | key                       | description |
  |---------------------------|-------------|
  |`:endpoint`                | OTLP endpoint, must start with `\"http://\"` or `\"https://\"` and include the full path (default: `\"http://localhost:4318/v1/metrics\"`).
  |`:headers`                 | HTTP headers to add to request (default: `{}`).
  |`:trusted-certificates-pem`| `^bytes` X.509 certificate chain in PEM format (default: system default trusted certificates).
  |`:compression-method`      | Method used to compress payloads, `\"gzip\"` or `\"none\"` (default: `\"none\"`).
  |`:timeout`                 | Maximum time to wait for export of a batch of spans. Value is either a `Duration` or a vector `[amount ^TimeUnit unit]` (default: 10s).
  |`:preferred-temporality`   | `^AggregationTemporality` Preferred aggregation temporality (default: `AggregationTemporality/CUMULATIVE`)."
  ([]
   (metric-exporter {}))
  ([{:keys [endpoint headers trusted-certificates-pem compression-method timeout preferred-temporality]}]
   (let [builder (cond-> (OtlpHttpMetricExporter/builder)
                         endpoint (.setEndpoint endpoint)
                         headers (add-headers headers)
                         trusted-certificates-pem (.setTrustedCertificates trusted-certificates-pem)
                         compression-method (.setCompression compression-method)
                         timeout (.setTimeout (util/duration timeout))
                         preferred-temporality (.setPreferredTemporality preferred-temporality))]
     (.build builder))))