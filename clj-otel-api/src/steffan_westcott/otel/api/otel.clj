(ns steffan-westcott.otel.api.otel
  "Entrypoint to the OpenTelemetry Java API."
  (:import (io.opentelemetry.api GlobalOpenTelemetry OpenTelemetry)))

(defn get-noop
  "Gets a no-op [[OpenTelemetry]] instance."
  []
  (OpenTelemetry/noop))

(defn get-global-otel!
  "Gets the global [[OpenTelemetry]] instance. If no instance has been
  previously set, this will attempt to configure an instance using
  the OpenTelemetry SDK autoconfigure module if present on the
  classpath. The configured instance (or a no-op instance if
  autoconfiguration did not occur or failed) will also be set as the
  global instance i.e. this function is side-effecting if no instance
  was previously set."
  []
  (GlobalOpenTelemetry/get))

(defn set-global-otel!
  "Sets the global [[OpenTelemetry]] instance."
  [open-telemetry]
  (GlobalOpenTelemetry/set open-telemetry))

(defn get-text-map-propagator
  "Gets the text map propagator of an [[OpenTelemetry]] instance. Takes the
  instance to use, or with no args the global [[OpenTelemetry]] instance is
  used."
  ([]
   (get-text-map-propagator (get-global-otel!)))
  ([^OpenTelemetry open-telemetry]
   (.getTextMapPropagator (.getPropagators open-telemetry))))
