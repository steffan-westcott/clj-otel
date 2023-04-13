(ns steffan-westcott.clj-otel.exporter.jaeger-grpc
  "Span data exporter to Jaeger via gRPC."
  (:require [steffan-westcott.clj-otel.util :as util])
  (:import (io.opentelemetry.exporter.jaeger JaegerGrpcSpanExporter)))

(defn span-exporter
  "Returns a span exporter that exports to Jaeger via gRPC, using Jaeger's
   protobuf model. May take an option map as follows:

   | key                       | description |
   |---------------------------|-------------|
   |`:endpoint`                | Jaeger endpoint (default: `\"http://localhost:14250\"`).
   |`:trusted-certificates-pem`| `^bytes` X.509 certificate chain in PEM format for verifying servers when TLS enabled (default: system default trusted certificates).
   |`:client-private-key-pem`  | `^bytes` private key in PEM format for verifying client when TLS enabled.
   |`:client-certificates-pem` | `^bytes` X.509 certificate chain in PEM format for verifying client when TLS enabled.
   |`:timeout`                 | Maximum time to wait for export of a batch of spans.  Value is either a `Duration` or a vector `[amount ^TimeUnit unit]` (default: 10s).
   |`:compression`             | Method used to compress payloads. Value is string `\"gzip\"` or `\"none\"` (default: `\"none\"`)."
  ([]
   (span-exporter {}))
  ([{:keys [endpoint trusted-certificates-pem client-private-key-pem client-certificates-pem timeout
            compression]}]
   (let [builder (cond-> (JaegerGrpcSpanExporter/builder)
                   endpoint    (.setEndpoint endpoint)
                   trusted-certificates-pem (.setTrustedCertificates trusted-certificates-pem)
                   (and client-private-key-pem client-certificates-pem)
                   (.setClientTls client-private-key-pem client-certificates-pem)

                   timeout     (.setTimeout (util/duration timeout))
                   compression (.setCompression compression))]
     (.build builder))))