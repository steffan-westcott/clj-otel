(ns ^:no-doc steffan-westcott.clj-otel.sdk.common
  "OpenTelemetry SDK common functions."
  (:import (io.opentelemetry.sdk.common InternalTelemetryVersion)))

(defn keyword->InternalTelemetryVersion
  "Given a keyword, returns InternalTelemetryVersion. Internal use only."
  ^InternalTelemetryVersion [k]
  (case k
    ; self-monitoring telemetry defined in SDK before semconv standardization
    :legacy InternalTelemetryVersion/LEGACY

    ; self-monitoring telemetry defined in latest semconv supported by this SDK
    :latest InternalTelemetryVersion/LATEST))
