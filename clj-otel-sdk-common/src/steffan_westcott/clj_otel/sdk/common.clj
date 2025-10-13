(ns ^:no-doc steffan-westcott.clj-otel.sdk.common
  "OpenTelemetry SDK common functions."
  (:import (io.opentelemetry.sdk.common InternalTelemetryVersion)))

#_{:clj-kondo/ignore [:missing-docstring]}
(def keyword->InternalTelemetryVersion
  {; self-monitoring telemetry defined in SDK before semconv standardization
   :legacy InternalTelemetryVersion/LEGACY

   ; self-monitoring telemetry defined in latest semconv supported by this SDK
   :latest InternalTelemetryVersion/LATEST})
