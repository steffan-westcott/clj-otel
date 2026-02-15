(ns steffan-westcott.clj-otel.instrumentation.runtime-telemetry-java8
  "Functions for registering measurements about the JVM runtime on Java 8+."
  (:require [steffan-westcott.clj-otel.api.otel :as otel])
  (:import (io.opentelemetry.api OpenTelemetry)
           (io.opentelemetry.instrumentation.runtimemetrics.java8 Classes
                                                                  Cpu
                                                                  GarbageCollector
                                                                  MemoryPools
                                                                  RuntimeMetrics
                                                                  Threads)
           (java.util List)))

(defn ^:deprecated register-classes!
  "DEPRECATED
   Registers measurements that generate metrics about JVM classes and returns a
   list of AutoCloseable."
  (^List []
   (register-classes! (otel/get-default-otel!)))
  (^List [open-telemetry]
   (Classes/registerObservers open-telemetry)))

(defn ^:deprecated register-cpu!
  "DEPRECATED
   Registers measurements that generate metrics about the CPU and returns a
   list of AutoCloseable."
  (^List []
   (register-cpu! (otel/get-default-otel!)))
  (^List [open-telemetry]
   (Cpu/registerObservers open-telemetry)))

(defn ^:deprecated register-garbage-collector!
  "DEPRECATED
   Registers measurements that generate metrics about the garbage collector and
   list a collection of AutoCloseable. May take an options map as follows:

   | key               | description |
   |-------------------|-------------|
   |`:capture-gc-cause`| If true, add garbage collection cause as an attribute (default: false)."
  (^List []
   (register-garbage-collector! (otel/get-default-otel!)))
  (^List [open-telemetry]
   (register-garbage-collector! open-telemetry {}))
  (^List
   [open-telemetry
    {:keys [capture-gc-cause]
     :or   {capture-gc-cause false}}]
   (GarbageCollector/registerObservers open-telemetry (boolean capture-gc-cause))))

(defn ^:deprecated register-memory-pools!
  "DEPRECATED
   Registers measurements that generate metrics about JVM memory pools and
   returns a list of AutoCloseable."
  (^List []
   (register-memory-pools! (otel/get-default-otel!)))
  (^List [open-telemetry]
   (MemoryPools/registerObservers open-telemetry)))

(defn ^:deprecated register-threads!
  "DEPRECATED
   Registers measurements that generate metrics about JVM threads and returns a
   list of AutoCloseable."
  (^List []
   (register-threads! (otel/get-default-otel!)))
  (^List [open-telemetry]
   (Threads/registerObservers open-telemetry)))

(defn register!
  "Registers measurements that generate all JVM runtime metrics and returns a
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

(defn close!
  "Stops generation of JVM metrics. Takes `RuntimeMetrics` returned by
   [[register!]]."
  [^RuntimeMetrics runtime-metrics]
  (.close runtime-metrics))
