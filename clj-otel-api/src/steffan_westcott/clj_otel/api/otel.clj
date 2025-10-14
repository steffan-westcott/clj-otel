(ns steffan-westcott.clj-otel.api.otel
  "Functions that operate on `io.opentelemetry.api.OpenTelemetry`, the
   entrypoint to telemetry functionality."
  (:import (io.opentelemetry.api GlobalOpenTelemetry OpenTelemetry)
           (io.opentelemetry.api.logs LoggerProvider)
           (io.opentelemetry.api.metrics MeterProvider)
           (io.opentelemetry.api.trace TracerProvider)
           (io.opentelemetry.context.propagation TextMapPropagator)))

(defonce ^:private default-otel
  (atom nil))

(defn get-noop
  "Gets a no-op `OpenTelemetry` instance."
  ^OpenTelemetry []
  (OpenTelemetry/noop))

(defn get-global-otel!
  "Gets the global `OpenTelemetry` instance declared by OpenTelemetry Java. If
   no instance has been previously set, this will attempt to configure an
   instance using the OpenTelemetry Java SDK autoconfigure module if present on
   the classpath. The configured instance (or a no-op instance if
   autoconfiguration failed or otherwise did not occur) will also be set as the
   global instance i.e. this function is side-effecting if no instance was
   previously set."
  ^OpenTelemetry []
  (GlobalOpenTelemetry/get))

(defn set-global-otel!
  "Sets the global `OpenTelemetry` instance declared by OpenTelemetry Java. The
   global instance may be set once only. Any attempts to set the global instance
   more than once will result in error."
  [^OpenTelemetry open-telemetry]
  (GlobalOpenTelemetry/set open-telemetry))

(defn set-default-otel!
  "Sets the default `OpenTelemetry` instance declared and used by `clj-otel`.
   Returns `otel`."
  ^OpenTelemetry [otel]
  (reset! default-otel otel))

(defn get-default-otel!
  "Gets the default `OpenTelemetry` instance declared and used by `clj-otel`. If
   no instance has been previously set, this falls back to the global instance
   declared by OpenTelemetry Java."
  ^OpenTelemetry []
  (swap! default-otel #(or % (get-global-otel!))))

(defn get-text-map-propagator
  "Gets the text map propagator of an `OpenTelemetry` instance `open-telemetry`
   or the default `OpenTelemetry` instance."
  (^TextMapPropagator []
   (get-text-map-propagator (get-default-otel!)))
  (^TextMapPropagator [^OpenTelemetry open-telemetry]
   (.getTextMapPropagator (.getPropagators open-telemetry))))

(defn get-tracer-provider
  "Gets the `TracerProvider` of an `OpenTelemetry` instance `open-telemetry`
   or the default `OpenTelemetry` instance."
  (^TracerProvider []
   (get-tracer-provider (get-default-otel!)))
  (^TracerProvider [^OpenTelemetry open-telemetry]
   (.getTracerProvider open-telemetry)))

(defn get-meter-provider
  "Gets the `MeterProvider` of an `OpenTelemetry` instance `open-telemetry`
   or the default `OpenTelemetry` instance."
  (^MeterProvider []
   (get-meter-provider (get-default-otel!)))
  (^MeterProvider [^OpenTelemetry open-telemetry]
   (.getMeterProvider open-telemetry)))

(defn get-logger-provider
  "Gets the `LoggerProvider` of an `OpenTelemetry` instance `open-telemetry`
   or the default `OpenTelemetry` instance."
  (^LoggerProvider []
   (get-logger-provider (get-default-otel!)))
  (^LoggerProvider [^OpenTelemetry open-telemetry]
   (.getLogsBridge open-telemetry)))
