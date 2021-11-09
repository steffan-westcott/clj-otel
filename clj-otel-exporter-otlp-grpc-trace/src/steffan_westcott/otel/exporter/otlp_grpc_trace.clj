(ns steffan-westcott.otel.exporter.otlp-grpc-trace
  "Span data exporter using OpenTelemetry Protocol via gRPC."
  (:require [steffan-westcott.otel.util :as util])
  (:import (io.opentelemetry.exporter.otlp.trace OtlpGrpcSpanExporter OtlpGrpcSpanExporterBuilder)))

(defn- ^OtlpGrpcSpanExporterBuilder add-headers [builder headers]
  (reduce-kv #(.addHeader ^OtlpGrpcSpanExporterBuilder %1 %2 %3) builder headers))

(defn span-exporter
  "Returns a span exporter that sends span data using OTLP via gRPC, using
  OpenTelemetry's protobuf model. Takes an option map as follows:

  | key                       | description |
  |---------------------------|-------------|
  |`:endpoint`                | OTLP endpoint, used with the default channel. Must start with `http://` or `https://` (default: `\"http://localhost:4317\"`).
  |`:headers`                 | HTTP headers to add to request, used with the default channel (default: {}).
  |`:trusted-certificates-pem`| ^bytes X.509 certificate chain in PEM format, used with the default channel (default: system default trusted certificates).
  |`:channel`                 | `ManagedChannel` instance to use for communication with the backend (default: `ManagedChannel` instance configured to use `:endpoint`, `:headers` amd `:trusted-certificates-pem`).
  |`:timeout`                 | Maximum time to wait for export of a batch of spans. Value is either a `Duration` or a vector `[amount ^TimeUnit unit]` (default: 10s)."
  ([]
   (span-exporter {}))
  ([{:keys [endpoint headers trusted-certificates-pem managed-channel timeout]
     :or   {headers {}}}]
   (let [builder (cond-> (OtlpGrpcSpanExporter/builder)
                         endpoint (.setEndpoint endpoint)
                         headers (add-headers headers)
                         trusted-certificates-pem (.setTrustedCertificates trusted-certificates-pem)
                         managed-channel (.setChannel managed-channel)
                         timeout (.setTimeout (util/duration timeout)))]
     (.build builder))))