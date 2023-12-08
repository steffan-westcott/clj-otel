(ns steffan-westcott.clj-otel.exporter.otlp.grpc.trace
  "Span data exporter using OpenTelemetry Protocol via gRPC."
  (:require [steffan-westcott.clj-otel.util :as util])
  (:import (io.opentelemetry.exporter.otlp.trace OtlpGrpcSpanExporter OtlpGrpcSpanExporterBuilder)))

(defn- add-headers
  ^OtlpGrpcSpanExporterBuilder [builder headers]
  (reduce-kv #(.addHeader ^OtlpGrpcSpanExporterBuilder %1 %2 %3) builder headers))

(defn span-exporter
  "Returns a span exporter that sends span data using OTLP via gRPC, using
   OpenTelemetry's protobuf model. May take an option map as follows:

   | key                       | description |
   |---------------------------|-------------|
   |`:endpoint`                | OTLP endpoint, must start with `\"http://\"` or `\"https://\"` (default: `\"http://localhost:4317\"`).
   |`:headers`                 | HTTP headers to add to request (default: `{}`).
   |`:trusted-certificates-pem`| `^bytes` X.509 certificate chain in PEM format for verifying servers when TLS enabled (default: system default trusted certificates).
   |`:client-private-key-pem`  | `^bytes` private key in PEM format for verifying client when TLS enabled.
   |`:client-certificates-pem` | `^bytes` X.509 certificate chain in PEM format for verifying client when TLS enabled.
   |`:ssl-context`             | `^SSLContext` \"bring your own SSLContext\" alternative to setting certificate bytes when using TLS.
   |`:x509-trust-manager`      | `^X509TrustManager` \"bring your own SSLContext\" alternative to setting certificate bytes when using TLS.
   |`:compression-method`      | Method used to compress payloads, `\"gzip\"` or `\"none\"` (default: `\"none\"`).
   |`:timeout`                 | Maximum time to wait for export of a batch of spans. Value is either a `Duration` or a vector `[amount ^TimeUnit unit]` (default: 10s).
   |`:meter-provider`          | ^MeterProvider to collect metrics related to export (default: metrics not collected)."
  (^OtlpGrpcSpanExporter []
   (span-exporter {}))
  (^OtlpGrpcSpanExporter
   [{:keys [endpoint headers trusted-certificates-pem client-private-key-pem client-certificates-pem
            ssl-context x509-trust-manager compression-method timeout meter-provider]}]
   (let [builder (cond-> (OtlpGrpcSpanExporter/builder)
                   endpoint (.setEndpoint endpoint)
                   headers (add-headers headers)
                   trusted-certificates-pem (.setTrustedCertificates trusted-certificates-pem)
                   (and client-private-key-pem client-certificates-pem)
                   (.setClientTls client-private-key-pem client-certificates-pem)

                   (and ssl-context x509-trust-manager) (.setSslContext ssl-context
                                                                        x509-trust-manager)
                   compression-method (.setCompression compression-method)
                   timeout (.setTimeout (util/duration timeout))
                   meter-provider (.setMeterProvider meter-provider))]
     (.build builder))))
