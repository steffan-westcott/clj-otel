(ns steffan-westcott.clj-otel.sdk.jaeger-remote-sampler
  "`Sampler` that implements Jaeger remote sampler type."
  (:require [steffan-westcott.clj-otel.sdk.tracer-provider :as tracer]
            [steffan-westcott.clj-otel.util :as util])
  (:import (io.opentelemetry.sdk.extension.trace.jaeger.sampler JaegerRemoteSampler)))

(defn jaeger-remote-sampler
  "Returns a `JaegerRemoteSampler`, a sampler that periodically obtains
   configuration from a remote Jaeger server. Takes an option map as follows:

   | key                       | description |
   |---------------------------|-------------|
   |`:service-name`            | Service name to be used by this sampler, required.
   |`:endpoint`                | Jaeger endpoint to connect to (default: `\"localhost:14250\"`).
   |`:trusted-certificates-pem`| `^bytes` X.509 certificate chain in PEM format for verifying servers when TLS enabled (default: system default trusted certificates).
   |`:client-private-key-pem`  | `^bytes` private key in PEM format for verifying client when TLS enabled.
   |`:client-certificates-pem` | `^bytes` X.509 certificate chain in PEM format for verifying client when TLS enabled.
   |`:ssl-context`             | `^SSLContext` \"bring your own SSLContext\" alternative to setting certificate bytes when using TLS.
   |`:x509-trust-manager`      | `^X509TrustManager` \"bring your own SSLContext\" alternative to setting certificate bytes when using TLS.
   |`:polling-interval`        | Polling interval for configuration updates. Value is either a `Duration` or a vector `[amount ^TimeUnit unit]` (default: 60s).
   |`:initial-sampler`         | Initial sampler that is used before sampling configuration is obtained (default: `{:parent-based {:root {:ratio 0.001}}}`)."
  ^JaegerRemoteSampler
  [{:keys [service-name endpoint trusted-certificates-pem client-private-key-pem
           client-certificates-pem ssl-context x509-trust-manager polling-interval
           initial-sampler]}]
  (let [builder (cond-> (.setServiceName (JaegerRemoteSampler/builder) service-name)
                  endpoint (.setEndpoint endpoint)
                  trusted-certificates-pem (.setTrustedCertificates trusted-certificates-pem)
                  (and client-private-key-pem client-certificates-pem)
                  (.setClientTls client-private-key-pem client-certificates-pem)

                  (and ssl-context x509-trust-manager) (.setSslContext ssl-context
                                                                       x509-trust-manager)
                  polling-interval (.setPollingInterval (util/duration polling-interval))
                  initial-sampler (.setInitialSampler (tracer/as-Sampler initial-sampler)))]
    (.build builder)))
