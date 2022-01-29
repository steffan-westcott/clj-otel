(ns steffan-westcott.clj-otel.sdk.jaeger-remote-sampler
  "`Sampler` that implements Jaeger remote sampler type."
  (:require [steffan-westcott.clj-otel.sdk.otel-sdk :as sdk]
            [steffan-westcott.clj-otel.util :as util])
  (:import (io.opentelemetry.sdk.extension.trace.jaeger.sampler JaegerRemoteSampler)))

(defn jaeger-remote-sampler
  "Returns a `JaegerRemoteSampler`, a sampler that periodically obtains
  configuration from a remote Jaeger server. Takes an option map as follows:

  | key               | description |
  |-------------------|-------------|
  |`:service-name`    | Service name to be used by this sampler, required.
  |`:endpoint`        | Jaeger endpoint to connect to (default: `\"localhost:14250\"`).
  |`:polling-interval`| Polling interval for configuration updates. Value is either a `Duration` or a vector `[amount ^TimeUnit unit]` (default: 60s).
  |`:initial-sampler` | Initial sampler that is used before sampling configuration is obtained (default: `{:parent-based {:root {:ratio 0.001}}}`)."
  [{:keys [service-name endpoint polling-interval initial-sampler]}]
  (let [builder (cond-> (.setServiceName (JaegerRemoteSampler/builder) service-name)
                        endpoint (.setEndpoint endpoint)
                        polling-interval (.setPollingInterval (util/duration polling-interval))
                        initial-sampler (.setInitialSampler (sdk/as-Sampler initial-sampler)))]
    (.build builder)))
