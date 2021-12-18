(ns steffan-westcott.otel.api.otel
  "Functions that operate on `io.opentelemetry.api.OpenTelemetry`,
  the entrypoint to telemetry functionality."
  (:import (io.opentelemetry.api GlobalOpenTelemetry OpenTelemetry)))

(defn get-noop
  "Gets a no-op `OpenTelemetry` instance."
  []
  (OpenTelemetry/noop))

(defn get-global-otel!
  "Gets the global `OpenTelemetry` instance. If no instance has been previously
  set, this will attempt to configure an instance using the OpenTelemetry SDK
  autoconfigure module if present on the classpath. The configured instance (or
  a no-op instance if autoconfiguration failed or otherwise did not occur) will
  also be set as the global instance i.e. this function is side-effecting if no
  instance was previously set."
  []
  (GlobalOpenTelemetry/get))

(defn set-global-otel!
  "Sets the global `OpenTelemetry` instance. This function may be evaluated
  once only. Any attempts to evaluate this more than once will result in an
  error."
  [open-telemetry]
  (GlobalOpenTelemetry/set open-telemetry))

(defn get-text-map-propagator
  "Gets the text map propagator of an `OpenTelemetry` instance
  `open-telemetry` or the global `OpenTelemetry` instance."
  ([]
   (get-text-map-propagator (get-global-otel!)))
  ([^OpenTelemetry open-telemetry]
   (.getTextMapPropagator (.getPropagators open-telemetry))))
