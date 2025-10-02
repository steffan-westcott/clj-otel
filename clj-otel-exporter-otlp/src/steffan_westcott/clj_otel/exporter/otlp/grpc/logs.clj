(ns steffan-westcott.clj-otel.exporter.otlp.grpc.logs
  "Log record data exporter using OpenTelemetry Protocol via gRPC."
  (:require [steffan-westcott.clj-otel.sdk.common :as common]
            [steffan-westcott.clj-otel.sdk.export :as export]
            [steffan-westcott.clj-otel.util :as util])
  (:import (io.opentelemetry.api.metrics MeterProvider)
           (io.opentelemetry.exporter.otlp.logs OtlpGrpcLogRecordExporter
                                                OtlpGrpcLogRecordExporterBuilder)))

(defn- add-headers
  ^OtlpGrpcLogRecordExporterBuilder [builder headers]
  (reduce-kv #(.addHeader ^OtlpGrpcLogRecordExporterBuilder %1 %2 %3) builder headers))

(defn log-record-exporter
  "Returns a log record exporter that sends log records using OTLP via gRPC, using
   OpenTelemetry's protobuf model. May take an option map as follows:

   | key                         | description |
   |-----------------------------|-------------|
   |`:endpoint`                  | OTLP endpoint, must start with `\"http://\"` or `\"https://\"` (default: `\"http://localhost:4317\"`).
   |`:headers`                   | HTTP headers to add to request (default: `{}`).
   |`:trusted-certificates-pem`  | `^bytes` X.509 certificate chain in PEM format for verifying servers when TLS enabled (default: system default trusted certificates).
   |`:client-private-key-pem`    | `^bytes` private key in PEM format for verifying client when TLS enabled.
   |`:client-certificates-pem`   | `^bytes` X.509 certificate chain in PEM format for verifying client when TLS enabled.
   |`:ssl-context`               | `^SSLContext` \"bring your own SSLContext\" alternative to setting certificate bytes when using TLS.
   |`:x509-trust-manager`        | `^X509TrustManager` \"bring your own SSLContext\" alternative to setting certificate bytes when using TLS.
   |`:compression-method`        | Method used to compress payloads, `\"gzip\"` or `\"none\"` (default: `\"none\"`).
   |`:timeout`                   | Maximum time to wait for export of a batch of log records. Value is either a `Duration` or a vector `[amount ^TimeUnit unit]` (default: 10s).
   |`:connect-timeout`           | Maximum time to wait for new connections to be established. Value is either a `Duration` or a vector `[amount ^TimeUnit unit]` (default: 10s).
   |`:retry-policy`              | Option map for retry policy, see `steffan-westcott.clj-otel.sdk.export/retry-policy` (default: same as `(retry-policy)`).
   |`:meter-provider`            | ^MeterProvider to collect metrics related to export (default: metrics not collected).
   |`:memory-mode`               | Either `:immutable-data` for thread safe or `:reusable-data` for non thread safe (but reduced) data allocations (default: `:reusable-data`).
   |`:service-classloader`       | ^ClassLoader to load the sender API.
   |`:internal-telemetry-version`| Self-monitoring telemetry to export, either `:legacy` or `:latest` (default: `:legacy`)."
  (^OtlpGrpcLogRecordExporter []
   (log-record-exporter {}))
  (^OtlpGrpcLogRecordExporter
   [{:keys [endpoint headers trusted-certificates-pem client-private-key-pem client-certificates-pem
            ssl-context x509-trust-manager compression-method timeout connect-timeout retry-policy
            ^MeterProvider meter-provider memory-mode service-classloader
            internal-telemetry-version]}]
   (let [builder (cond-> (OtlpGrpcLogRecordExporter/builder)
                   endpoint (.setEndpoint endpoint)
                   headers (add-headers headers)
                   trusted-certificates-pem (.setTrustedCertificates trusted-certificates-pem)
                   (and client-private-key-pem client-certificates-pem)
                   (.setClientTls client-private-key-pem client-certificates-pem)

                   (and ssl-context x509-trust-manager) (.setSslContext ssl-context
                                                                        x509-trust-manager)
                   compression-method (.setCompression compression-method)
                   timeout (.setTimeout (util/duration timeout))
                   connect-timeout (.setConnectTimeout (util/duration connect-timeout))
                   retry-policy (.setRetryPolicy (export/retry-policy retry-policy))
                   meter-provider (.setMeterProvider meter-provider)
                   memory-mode (.setMemoryMode (export/keyword->MemoryMode memory-mode))
                   service-classloader (.setServiceClassLoader service-classloader)
                   internal-telemetry-version (.setInternalTelemetryVersion
                                               (common/keyword->InternalTelemetryVersion
                                                internal-telemetry-version)))]
     (.build builder))))
