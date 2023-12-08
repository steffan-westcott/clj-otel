(ns ^{:deprecated "0.2.4.1"} steffan-westcott.clj-otel.exporter.jaeger-grpc
  "Span data exporter to Jaeger via gRPC. Deprecated - Use
   `steffan-westcott.clj-otel.exporter.otlp.grpc.trace` or
   `steffan-westcott.clj-otel.exporter.otlp.http.trace` instead."
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
   |`:ssl-context`             | `^SSLContext` \"bring your own SSLContext\" alternative to setting certificate bytes when using TLS.
   |`:x509-trust-manager`      | `^X509TrustManager` \"bring your own SSLContext\" alternative to setting certificate bytes when using TLS.
   |`:timeout`                 | Maximum time to wait for export of a batch of spans.  Value is either a `Duration` or a vector `[amount ^TimeUnit unit]` (default: 10s).
   |`:compression`             | Method used to compress payloads. Value is string `\"gzip\"` or `\"none\"` (default: `\"none\"`)."
  (^JaegerGrpcSpanExporter []
   (span-exporter {}))
  (^JaegerGrpcSpanExporter
   [{:keys [endpoint trusted-certificates-pem client-private-key-pem client-certificates-pem
            ssl-context x509-trust-manager timeout compression]}]
   (let [builder (cond-> (JaegerGrpcSpanExporter/builder)
                   endpoint (.setEndpoint endpoint)
                   trusted-certificates-pem (.setTrustedCertificates trusted-certificates-pem)
                   (and client-private-key-pem client-certificates-pem)
                   (.setClientTls client-private-key-pem client-certificates-pem)

                   (and ssl-context x509-trust-manager) (.setSslContext ssl-context
                                                                        x509-trust-manager)
                   timeout (.setTimeout (util/duration timeout))
                   compression (.setCompression compression))]
     (.build builder))))