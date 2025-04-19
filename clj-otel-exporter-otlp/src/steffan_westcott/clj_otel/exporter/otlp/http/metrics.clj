(ns steffan-westcott.clj-otel.exporter.otlp.http.metrics
  "Metric data exporter using OpenTelemetry Protocol via HTTP."
  (:require [steffan-westcott.clj-otel.sdk.export :as export]
            [steffan-westcott.clj-otel.util :as util])
  (:import (io.opentelemetry.exporter.otlp.http.metrics OtlpHttpMetricExporter
                                                        OtlpHttpMetricExporterBuilder)
           (io.opentelemetry.sdk.metrics.export AggregationTemporalitySelector)))

(defn- add-headers
  ^OtlpHttpMetricExporterBuilder [builder headers]
  (reduce-kv #(.addHeader ^OtlpHttpMetricExporterBuilder %1 %2 %3) builder headers))

(defn metric-exporter
  "Returns a metric data exporter that sends span data using OTLP via HTTP,
   using OpenTelemetry's protobuf model. May take an option map as follows:

   | key                               | description |
   |-----------------------------------|-------------|
   |`:endpoint`                        | OTLP endpoint, must start with `\"http://\"` or `\"https://\"` and include the full path (default: `\"http://localhost:4318/v1/metrics\"`).
   |`:headers`                         | HTTP headers to add to request (default: `{}`).
   |`:trusted-certificates-pem`        | `^bytes` X.509 certificate chain in PEM format for verifying servers when TLS enabled (default: system default trusted certificates).
   |`:client-private-key-pem`          | `^bytes` private key in PEM format for verifying client when TLS enabled.
   |`:client-certificates-pem`         | `^bytes` X.509 certificate chain in PEM format for verifying client when TLS enabled.
   |`:ssl-context`                     | `^SSLContext` \"bring your own SSLContext\" alternative to setting certificate bytes when using TLS.
   |`:x509-trust-manager`              | `^X509TrustManager` \"bring your own SSLContext\" alternative to setting certificate bytes when using TLS.
   |`:compression-method`              | Method used to compress payloads, `\"gzip\"` or `\"none\"` (default: `\"none\"`).
   |`:timeout`                         | Maximum time to wait for export of a batch of spans. Value is either a `Duration` or a vector `[amount ^TimeUnit unit]` (default: 10s).
   |`:connect-timeout`                 | Maximum time to wait for new connections to be established. Value is either a `Duration` or a vector `[amount ^TimeUnit unit]` (default: 10s).
   |`:retry-policy`                    | Option map for retry policy, see `steffan-westcott.clj-otel.sdk.export/retry-policy` (default: same as `(retry-policy)`).
   |`:proxy-options`                   | Option map for proxy options, see `steffan-westcott.clj-otel.sdk.export/proxy-options` (default: no proxy used).
   |`:aggregation-temporality-selector`| Function which takes an `InstrumentType` and returns an `AggregationTemporality` (default: constantly `AggregationTemporality/CUMULATIVE`).
   |`:memory-mode`                     | Either `:immutable-data` for thread safe or `:reusable-data` for non thread safe (but reduced) data allocations (default: `:immutable-data`)."
  (^OtlpHttpMetricExporter []
   (metric-exporter {}))
  (^OtlpHttpMetricExporter
   [{:keys [endpoint headers trusted-certificates-pem client-private-key-pem client-certificates-pem
            ssl-context x509-trust-manager compression-method timeout connect-timeout retry-policy
            proxy-options aggregation-temporality-selector memory-mode]}]
   (let [builder
         (cond-> (OtlpHttpMetricExporter/builder)
           endpoint (.setEndpoint endpoint)
           headers (add-headers headers)
           trusted-certificates-pem (.setTrustedCertificates trusted-certificates-pem)
           (and client-private-key-pem client-certificates-pem)
           (.setClientTls client-private-key-pem client-certificates-pem)

           (and ssl-context x509-trust-manager) (.setSslContext ssl-context x509-trust-manager)
           compression-method (.setCompression compression-method)
           timeout (.setTimeout (util/duration timeout))
           connect-timeout (.setConnectTimeout (util/duration connect-timeout))
           retry-policy (.setRetryPolicy (export/retry-policy retry-policy))
           proxy-options (.setProxyOptions (export/proxy-options proxy-options))
           aggregation-temporality-selector (.setAggregationTemporalitySelector
                                             (reify
                                              AggregationTemporalitySelector
                                                (getAggregationTemporality [_ instrument-type]
                                                  (aggregation-temporality-selector
                                                   instrument-type))))
           memory-mode (.setMemoryMode (export/keyword->MemoryMode memory-mode)))]
     (.build builder))))
