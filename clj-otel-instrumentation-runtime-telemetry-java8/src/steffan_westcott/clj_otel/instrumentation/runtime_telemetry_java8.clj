(ns ^:deprecated steffan-westcott.clj-otel.instrumentation.runtime-telemetry-java8
  "DEPRECATED - Use module clj-otel-instrumentation-runtime-telemetry instead.
   Functions for registering measurements about the JVM runtime on Java 8+."
  (:require [steffan-westcott.clj-otel.api.otel :as otel])
  (:import (io.opentelemetry.api OpenTelemetry)
           (io.opentelemetry.instrumentation.runtimemetrics.java8 RuntimeMetrics)))

(defn ^:deprecated register!
  "DEPRECATED - Use module clj-otel-instrumentation-runtime-telemetry instead.
   Registers measurements that generate all JVM runtime metrics and returns a
   `RuntimeMetrics` instance that may be used to stop generation (see
    [[close!]]). `open-telemetry` is an `OpenTelemetry` instance where to
    register the metrics. May take an option map as follows:

   | key                          | description |
   |------------------------------|-------------|
   |`:emit-experimental-telemetry`| If true, enable all JMX telemetry collection (default: false).
   |`:capture-gc-cause`           | If true, enable the capture of the `jvm.gc.cause` attribute with the `jvm.gc.duration` metric (default: false)."
  (^RuntimeMetrics []
   (register! (otel/get-default-otel!)))
  (^RuntimeMetrics [^OpenTelemetry open-telemetry]
   (register! open-telemetry {}))
  (^RuntimeMetrics
   [^OpenTelemetry open-telemetry {:keys [emit-experimental-telemetry capture-gc-cause]}]
   (let [builder (cond-> (RuntimeMetrics/builder open-telemetry)
                   emit-experimental-telemetry (.emitExperimentalTelemetry)
                   capture-gc-cause (.captureGcCause))]
     (.build builder))))

(defn ^:deprecated close!
  "DEPRECATED - Use module clj-otel-instrumentation-runtime-telemetry instead.
   Stops generation of JVM metrics. Takes `RuntimeMetrics` returned by
   [[register!]]."
  [^RuntimeMetrics runtime-metrics]
  (.close runtime-metrics))
