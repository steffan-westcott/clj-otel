(ns steffan-westcott.otel.exporter.jaeger-grpc
  "Span data exporter to Jaeger via GRPC."
  (:require [steffan-westcott.otel.util :as util])
  (:import (io.opentelemetry.exporter.jaeger JaegerGrpcSpanExporter)))

(defn span-exporter
  "Returns a span exporter that exports to Jaeger via gRPC, using Jaeger's
  protobuf model. Takes an option map as follows:

  | key       | description |
  |-----------|-------------|
  |`:endpoint`| Jaeger endpoint, used with the default `:channel` (default: `\"http://localhost:14250\"`).
  |`:timeout` | Maximum time to wait for export of a batch of spans.  Value is either a [[Duration]] or a vector `[amount ^TimeUnit unit]` (default: 10s).
  |`:channel` | [[ManagedChannel]] instance to use for communication with the backend (default: [[ManagedChannel]] instance configured to use `:endpoint`)."
  ([]
   (span-exporter {}))
  ([{:keys [endpoint timeout channel]}]
   (let [builder (cond-> (JaegerGrpcSpanExporter/builder)
                         endpoint (.setEndpoint endpoint)
                         timeout (.setTimeout (util/duration timeout))
                         channel (.setChannel channel))]
     (.build builder))))