(ns steffan-westcott.clj-otel.exporter.otlp.grpc.metrics
  "Metric data exporter using OpenTelemetry Protocol via gRPC."
  (:require [steffan-westcott.clj-otel.api.otel :as otel]
            [steffan-westcott.clj-otel.sdk.common :as common]
            [steffan-westcott.clj-otel.sdk.export :as export]
            [steffan-westcott.clj-otel.util :as util])
  (:import (io.opentelemetry.exporter.otlp.metrics OtlpGrpcMetricExporter
                                                   OtlpGrpcMetricExporterBuilder)
           (io.opentelemetry.sdk.metrics.export AggregationTemporalitySelector
                                                DefaultAggregationSelector)))

(defn- add-headers
  ^OtlpGrpcMetricExporterBuilder [builder headers]
  (reduce-kv #(.addHeader ^OtlpGrpcMetricExporterBuilder %1 %2 %3) builder headers))

(defn metric-exporter
  "Returns a metric data exporter that sends span data using OTLP via gRPC,
   using OpenTelemetry's protobuf model. May take an option map as follows:

   | key                               | description |
   |-----------------------------------|-------------|
   |`:endpoint`                        | OTLP endpoint, must start with `\"http://\"` or `\"https://\"` (default: `\"http://localhost:4317\"`).
   |`:headers`                         | HTTP headers to add to request (default: `{}`).
   |`:trusted-certificates-pem`        | `^bytes` X.509 certificate chain in PEM format for verifying servers when TLS enabled (default: system default trusted certificates).
   |`:client-private-key-pem`          | `^bytes` private key in PEM format for verifying client when TLS enabled.
   |`:client-certificates-pem`         | `^bytes` X.509 certificate chain in PEM format for verifying client when TLS enabled.
   |`:ssl-context`                     | `SSLContext` \"bring your own SSLContext\" alternative to setting certificate bytes when using TLS.
   |`:x509-trust-manager`              | `X509TrustManager` \"bring your own SSLContext\" alternative to setting certificate bytes when using TLS.
   |`:compression-method`              | Method used to compress payloads, `\"gzip\"` or `\"none\"` (default: `\"none\"`).
   |`:timeout`                         | Maximum time to wait for export of a batch of spans. Value is either a `Duration` or a vector `[amount ^TimeUnit unit]` (default: 10s).
   |`:connect-timeout`                 | Maximum time to wait for new connections to be established. Value is either a `Duration` or a vector `[amount ^TimeUnit unit]` (default: 10s).
   |`:retry-policy`                    | Option map for retry policy, see `steffan-westcott.clj-otel.sdk.export/retry-policy` (default: same as `(retry-policy)`).
   |`:meter-provider-fn`               | fn that returns `MeterProvider` to collect metrics related to export (default: meter provider of default OpenTelemetry).
   |`:aggregation-temporality-selector`| Function which takes an `InstrumentType` and returns an `AggregationTemporality` (default: same as constantly `AggregationTemporality/CUMULATIVE`).
   |`:default-aggregation-selector`    | Function which takes an `InstrumentType` and returns default `Aggregation` (default: same as `DefaultAggregationSelector/getDefault`).
   |`:memory-mode`                     | Either `:immutable-data` for thread safe or `:reusable-data` for non thread safe (but reduced) data allocations (default: `:reusable-data`).
   |`:service-classloader`             | `ClassLoader` to load the sender API.
   |`:component-loader`                | `ComponentLoader` to load the sender API.
   |`:executor-service`                | `ExecutorService` used to execute requests.
   |`:internal-telemetry-version`      | Self-monitoring telemetry to export, either `:legacy` or `:latest` (default: `:legacy`)."
  (^OtlpGrpcMetricExporter []
   (metric-exporter {}))
  (^OtlpGrpcMetricExporter
   [{:keys [endpoint headers trusted-certificates-pem client-private-key-pem client-certificates-pem
            ssl-context x509-trust-manager compression-method timeout connect-timeout retry-policy
            meter-provider-fn aggregation-temporality-selector default-aggregation-selector
            memory-mode service-classloader component-loader executor-service
            internal-telemetry-version]
     :or   {meter-provider-fn otel/get-meter-provider}}]
   (let [builder
         (cond-> (OtlpGrpcMetricExporter/builder)
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
           :always (.setMeterProvider (util/supplier meter-provider-fn))
           aggregation-temporality-selector (.setAggregationTemporalitySelector
                                             (reify
                                              AggregationTemporalitySelector
                                                (getAggregationTemporality [_ instrument-type]
                                                  (aggregation-temporality-selector
                                                   instrument-type))))
           default-aggregation-selector (.setDefaultAggregationSelector
                                         (reify
                                          DefaultAggregationSelector
                                            (getDefaultAggregation [_ instrument-type]
                                              (default-aggregation-selector instrument-type))))
           memory-mode (.setMemoryMode (export/keyword->MemoryMode memory-mode))
           service-classloader (.setServiceClassLoader service-classloader)
           component-loader (.setComponentLoader component-loader)
           executor-service (.setExecutorService executor-service)
           internal-telemetry-version (.setInternalTelemetryVersion
                                       (common/keyword->InternalTelemetryVersion
                                        internal-telemetry-version)))]
     (.build builder))))
