(ns steffan-westcott.clj-otel.instrumentation.runtime-telemetry-java17
  "Functions for registering measurements about the JVM runtime on Java 17+."
  (:require [steffan-westcott.clj-otel.api.otel :as otel])
  (:import (io.opentelemetry.instrumentation.runtimemetrics.java17 RuntimeMetrics
                                                                   RuntimeMetricsBuilder)))

(defn- put-jfr-features
  ^RuntimeMetricsBuilder [^RuntimeMetricsBuilder builder jfr]
  (if (map? jfr)
    (reduce-kv (fn [^RuntimeMetricsBuilder b feature enabled?]
                 (if enabled?
                   (.enableFeature b feature)
                   (.disableFeature b feature)))
               builder
               jfr)
    (if jfr
      (.enableAllFeatures builder)
      (.disableAllFeatures builder))))

(defn register!
  "Registers measurements that generate all JVM runtime metrics, using some
   combination of JMX and JFR, and returns a `RuntimeMetrics` instance that may
   be used to stop generation (see [[close!]]). `open-telemetry` is an
   `OpenTelemetry` instance where to register the metrics. See
   `io.opentelemetry.instrumentation.runtimemetrics.java17.JfrFeature` for more
   details on JFR features that may be enabled.

   By default, all JMX and some JFR features are enabled to register a maximal
   selection of measurements. For a finer level of control, use the option map
   `opts` as follows:

   | key  | description |
   |------|-------------|
   |`:jmx`| If true, include all JMX measurements, otherwise do not include any JMX measurements (default: true)
   |`:jfr`| Either a map or boolean value. If a map, the map keys are `JfrFeature` enum values, and the map boolean values determine if the feature should be enabled (true) or disabled (false). If not a map, the value determines if all JFR features should be enabled (true) or disabled (false) (default: See JfrFeature for each JFR feature default)."
  ([]
   (register! {}))
  ([opts]
   (register! (otel/get-default-otel!) opts))
  ([open-telemetry
    {:keys [jmx jfr]
     :or   {jmx true}}]
   (let [builder (cond-> (RuntimeMetrics/builder open-telemetry)
                   (not jmx)   (.disableAllJmx)
                   (some? jfr) (put-jfr-features jfr))]
     (.build builder))))

(defn close!
  "Stops generation of JVM metrics. Takes `runtime-metrics` returned by
   [[register!]]."
  [^RuntimeMetrics runtime-metrics]
  (.close runtime-metrics))
