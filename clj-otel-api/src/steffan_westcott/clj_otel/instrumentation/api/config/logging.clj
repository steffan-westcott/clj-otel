(ns steffan-westcott.clj-otel.instrumentation.api.config.logging
  "Common configuration properties for logging."
  (:require [steffan-westcott.clj-otel.instrumentation.api.config :as config]))

(defn trace-id-key
  "Returns logging context data key for trace id."
  []
  (config/get-string "otel.instrumentation.common.logging.trace-id" "trace_id"))

(defn span-id-key
  "Returns logging context data key for span id."
  []
  (config/get-string "otel.instrumentation.common.logging.span-id" "span_id"))

(defn trace-flags-key
  "Returns logging context data key for trace flags."
  []
  (config/get-string "otel.instrumentation.common.logging.trace-flags" "trace_flags"))
