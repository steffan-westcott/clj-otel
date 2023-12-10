(ns steffan-westcott.clj-otel.sdk.export
  "Utilities for OpenTelemetry SDK exporters."
  (:require [steffan-westcott.clj-otel.util :as util])
  (:import (io.opentelemetry.sdk.common.export RetryPolicy)))

(defn retry-policy
  "Builds and returns a `RetryPolicy` object. May take an option map as
   follows:

   | key                 | description |
   |---------------------|-------------|
   |`:max-attempts`      | Maximum number of attempts, including the original request. Must be in range 1 to 5 inclusive (default: 5).
   |`:initial-backoff`   | Initial backoff duration. Must be greater than 0. Value is either a `Duration` or a vector `[amount ^TimeUnit unit]` (default: 1s).
   |`:max-backoff`       | Maximum backoff duration. Must be greater than 0. Value is either a `Duration` or a vector `[amount ^TimeUnit unit]` (default: 5s).
   |`:backoff-multiplier`| Backoff multiplier, as a `double`. Must be greater than 0.0 (default: 1.5)."
  (^RetryPolicy [] (retry-policy {}))
  (^RetryPolicy [{:keys [max-attempts initial-backoff max-backoff backoff-multiplier]}]
   (let [builder (cond-> (RetryPolicy/builder)
                   max-attempts       (.setMaxAttempts max-attempts)
                   initial-backoff    (.setInitialBackoff (util/duration initial-backoff))
                   max-backoff        (.setMaxBackoff (util/duration max-backoff))
                   backoff-multiplier (.setBackoffMultiplier backoff-multiplier))]
     (.build builder))))
