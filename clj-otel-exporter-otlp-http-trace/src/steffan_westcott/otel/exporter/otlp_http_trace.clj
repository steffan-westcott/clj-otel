(ns steffan-westcott.otel.exporter.otlp-http-trace
  "Span data exporter using OpenTelemetry Protocol via HTTP."
  (:require [steffan-westcott.otel.util :as util])
  (:import (io.opentelemetry.exporter.otlp.http.trace OtlpHttpSpanExporter OtlpHttpSpanExporterBuilder)))

(defn- ^OtlpHttpSpanExporterBuilder add-headers [builder headers]
  (reduce-kv #(.addHeader ^OtlpHttpSpanExporterBuilder %1 %2 %3) builder headers))

(defn span-exporter
  "Returns a span exporter that sends span data using OTLP via HTTP, using
  OpenTelemetry's protobuf model. May take an option map as follows:

  | key                       | description |
  |---------------------------|-------------|
  |`:endpoint`                | OTLP endpoint to connect to, must start with `\"http://\"` or `\"https://\"` and include the full path (default: `\"http://localhost:4318/v1/traces\"`)
  |`:headers`                 | HTTP headers to add to request (default: `{}`).
  |`:trusted-certificates-pem`| `^bytes` X.509 certificate chain in PEM format (default: system default trusted certificates).
  |`:compression-method`      | Method used to compress payloads, `\"gzip\"` or `\"none\"` (default: `\"none\"`)
  |`:timeout`                 | Maximum time to wait for export of a batch of spans. Value is either a `Duration` or a vector `[amount ^TimeUnit unit]` (default: 10s)."
  ([]
   (span-exporter {}))
  ([{:keys [endpoint headers trusted-certificates-pem compression-method timeout]
     :or   {headers {}}}]
   (let [builder (cond-> (OtlpHttpSpanExporter/builder)
                         endpoint (.setEndpoint endpoint)
                         headers (add-headers headers)
                         trusted-certificates-pem (.setTrustedCertificates trusted-certificates-pem)
                         compression-method (.setCompression compression-method)
                         timeout (.setTimeout (util/duration timeout)))]
     (.build builder))))