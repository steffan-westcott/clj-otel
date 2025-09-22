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

   | key                     | description |
   |-------------------------|-------------|
   |`:set-as-default`        | If true, sets the configured SDK instance as the default `OpenTelemetry` instance declared and used by `clj-otel` (default: `true`).
   |`:set-as-global`         | If true, sets the configured SDK instance as the global `OpenTelemetry` instance declared by Java OpenTelemetry (default: `false`).
   |`:register-shutdown-hook`| If true, registers a JVM shutdown hook to close the configured SDK instance (default: `true`).
   |`:prop-overrides`        | fn that takes io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties and returns a string map of config property names and values to override defaults. Config property lookup precedence is: system property > env var > override > default."
  (^OpenTelemetrySdk [] (init-otel-sdk! {}))
  (^OpenTelemetrySdk
   [{:keys [set-as-default set-as-global register-shutdown-hook prop-overrides]
     :or   {set-as-default         true
            set-as-global          false
            register-shutdown-hook true}}]
   (let [builder  (cond-> (AutoConfiguredOpenTelemetrySdk/builder)
                    set-as-global  (.setResultAsGlobal)
                    (not register-shutdown-hook) (.disableShutdownHook)
                    prop-overrides (.addPropertiesCustomizer (util/function prop-overrides)))
         auto-sdk (.build builder)
         sdk      (.getOpenTelemetrySdk auto-sdk)]
     (when set-as-default
       (otel/set-default-otel! sdk))
     sdk)))
