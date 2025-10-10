(ns steffan-westcott.clj-otel.sdk.autoconfigure
  "Configuration of the OpenTelemetry SDK with environment variables and system
   properties. See https://github.com/open-telemetry/opentelemetry-java/tree/main/sdk-extensions/autoconfigure
   for configuration options."
  (:require [steffan-westcott.clj-otel.api.otel :as otel]
            [steffan-westcott.clj-otel.util :as util])
  (:import (io.opentelemetry.sdk OpenTelemetrySdk)
           (io.opentelemetry.sdk.autoconfigure AutoConfiguredOpenTelemetrySdk)))


(defn init-otel-sdk!
  "Returns an `OpenTelemetrySdk` instance, configured by the OpenTelemetry Java
   SDK autoconfigure module. Optionally also sets the configured SDK as the
   default `OpenTelemetry` instance used by `clj-otel` and Java OpenTelemetry
   and registers a shutdown hook to close it.

   See `steffan-westcott.clj-otel.sdk.otel-sdk/close-otel-sdk!` for an
   alternative to using the shutdown hook.

   Takes an option map as follows:

   | key                         | description |
   |-----------------------------|-------------|
   |`:set-as-default`            | If true, sets the configured SDK instance as the default `OpenTelemetry` instance declared and used by `clj-otel` (default: `true`).
   |`:set-as-global`             | If true, sets the configured SDK instance as the global `OpenTelemetry` instance declared by Java OpenTelemetry (default: `false`).
   |`:register-shutdown-hook`    | If true, registers a JVM shutdown hook to close the configured SDK instance (default: `true`).
   |`:service-class-loader`      | `ClassLoader` to use for loading SPI implementations (default: same as for `AutoConfiguredOpenTelemetrySdk` class).
   |`:component-loader`          | `ComponentLoader` to use for loading SPI implementations (default: same as for `AutoConfiguredOpenTelemetrySdk` class).
   |                             |
   |`:prop-overrides`            | fn that takes `ConfigProperties` and returns a string map of config property names and values to override defaults. Config property lookup precedence is: system property > env var > override > default.
   |`:resource-fn`               | fn that takes `Resource` and `ConfigProperties`, and returns transformed `Resource` to use (default: no transform).
   |                             |
   |`:tracer-provider-builder-fn`| fn that takes `SdkTracerProviderBuilder` and `ConfigProperties`, and returns transformed `SdkTracerProviderBuilder` to use (default: no transform).
   |`:span-exporter-fn`          | fn that takes `SpanExporter` and `ConfigProperties`, and returns transformed `SpanExporter` to use (default: no transform).
   |`:span-processor-fn`         | fn that takes `SpanProcessor` and `ConfigProperties`, and returns transformed `SpanProcessor` to use (default: no transform).
   |`:sampler-fn`                | fn that takes `Sampler` and `ConfigProperties`, and returns transformed `Sampler` to use (default: no transform).
   |`:text-map-propagator-fn`    | fn that takes `TextMapPropagator` and `ConfigProperties`, and returns transformed `TextMapPropagator` to use (default: no transform).
   |                             |
   |`:meter-provider-builder-fn` | fn that takes `SdkMeterProviderBuilder` and `ConfigProperties`, and returns transformed `SdkMeterProviderBuilder` to use (default: no transform).
   |`:metric-exporter-fn`        | fn that takes `MetricExporter` and `ConfigProperties`, and returns transformed `MetricExporter` to use (default: no transform).
   |`:metric-reader-fn`          | fn that takes `MetricReader` and `ConfigProperties`, and returns transformed `MetricReader` to use (default: no transform).
   |                             |
   |`:logger-provider-builder-fn`| fn that takes `SdkLoggerProviderBuilder` and `ConfigProperties`, and returns transformed `SdkLoggerProviderBuilder` to use (default: no transform).
   |`:log-record-exporter-fn`    | fn that takes `LogRecordExporter` and `ConfigProperties`, and returns transformed `LogRecordExporter` to use (default: no transform).
   |`:log-record-processor-fn`   | fn that takes `LogRecordProcessor` and `ConfigProperties`, and returns transformed `LogRecordProcessor` to use (default: no transform)."
  (^OpenTelemetrySdk [] (init-otel-sdk! {}))
  (^OpenTelemetrySdk
   [{:keys [set-as-default set-as-global register-shutdown-hook service-class-loader
            component-loader prop-overrides resource-fn tracer-provider-builder-fn span-exporter-fn
            span-processor-fn sampler-fn text-map-propagator-fn meter-provider-builder-fn
            metric-exporter-fn metric-reader-fn logger-provider-builder-fn log-record-exporter-fn
            log-record-processor-fn]
     :or   {set-as-default         true
            set-as-global          false
            register-shutdown-hook true}}]
   (let [builder
         (cond-> (AutoConfiguredOpenTelemetrySdk/builder)
           set-as-global             (.setResultAsGlobal)
           (not register-shutdown-hook) (.disableShutdownHook)
           service-class-loader      (.setServiceClassLoader service-class-loader)
           component-loader          (.setComponentLoader component-loader)
           ;;
           prop-overrides            (.addPropertiesCustomizer (util/function prop-overrides))
           resource-fn               (.addResourceCustomizer (util/bifunction resource-fn))
           ;;
           tracer-provider-builder-fn (.addTracerProviderCustomizer (util/bifunction
                                                                     tracer-provider-builder-fn))
           span-exporter-fn          (.addSpanExporterCustomizer (util/bifunction span-exporter-fn))
           span-processor-fn         (.addSpanProcessorCustomizer (util/bifunction
                                                                   span-processor-fn))
           sampler-fn                (.addSamplerCustomizer (util/bifunction sampler-fn))
           text-map-propagator-fn    (.addPropagatorCustomizer (util/bifunction
                                                                text-map-propagator-fn))
           ;;
           meter-provider-builder-fn (.addMeterProviderCustomizer (util/bifunction
                                                                   meter-provider-builder-fn))
           metric-exporter-fn        (.addMetricExporterCustomizer (util/bifunction
                                                                    metric-exporter-fn))
           metric-reader-fn          (.addMetricReaderCustomizer (util/bifunction metric-reader-fn))
           ;;
           logger-provider-builder-fn (.addLoggerProviderCustomizer (util/bifunction
                                                                     logger-provider-builder-fn))
           log-record-exporter-fn    (.addLogRecordExporterCustomizer (util/bifunction
                                                                       log-record-exporter-fn))
           log-record-processor-fn   (.addLogRecordProcessorCustomizer (util/bifunction
                                                                        log-record-processor-fn)))

         auto-sdk (.build builder)
         sdk (.getOpenTelemetrySdk auto-sdk)]
     (when set-as-default
       (otel/set-default-otel! sdk))
     sdk)))
