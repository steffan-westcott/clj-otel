(ns steffan-westcott.clj-otel.api.otel
  "Functions that operate on `io.opentelemetry.api.OpenTelemetry`, the
   entrypoint to telemetry functionality."
  (:import (io.opentelemetry.api GlobalOpenTelemetry OpenTelemetry)
           (io.opentelemetry.context.propagation TextMapPropagator)))

(defonce ^:private default-otel
  (atom nil))

(defn get-noop
  "Gets a no-op `OpenTelemetry` instance."
  []
  (OpenTelemetry/noop))

(defn get-global-otel!
  "Gets the global `OpenTelemetry` instance declared by OpenTelemetry Java. If
   no instance has been previously set, this will attempt to configure an
   instance using the OpenTelemetry Java SDK autoconfigure module if present on
   the classpath. The configured instance (or a no-op instance if
   autoconfiguration failed or otherwise did not occur) will also be set as the
   global instance i.e. this function is side-effecting if no instance was
   previously set."
  []
  (GlobalOpenTelemetry/get))

(defn set-global-otel!
  "Sets the global `OpenTelemetry` instance declared by OpenTelemetry Java. The
   global instance may be set once only. Any attempts to set the global instance
   more than once will result in error."
  [open-telemetry]
  (GlobalOpenTelemetry/set open-telemetry))

(defn set-default-otel!
  "Sets the default `OpenTelemetry` instance declared and used by `clj-otel`.
   Returns `otel`."
  [otel]
  (reset! default-otel otel))

(defn get-default-otel!
  "Gets the default `OpenTelemetry` instance declared and used by `clj-otel`. If
   no instance has been previously set, this falls back to the global instance
   declared by OpenTelemetry Java."
  []
  (if-let [otel @default-otel]
    otel
    (set-default-otel! (get-global-otel!))))

(defn get-text-map-propagator
  "Gets the text map propagator of an `OpenTelemetry` instance `open-telemetry`
   or the default `OpenTelemetry` instance."
  (^TextMapPropagator []
   (get-text-map-propagator (get-default-otel!)))
  (^TextMapPropagator [^OpenTelemetry open-telemetry]
   (.getTextMapPropagator (.getPropagators open-telemetry))))
