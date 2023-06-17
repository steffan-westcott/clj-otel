(ns steffan-westcott.clj-otel.instrumentation.runtime-telemetry-java8
  "Functions for registering measurements about the JVM runtime on Java 8+."
  (:require [steffan-westcott.clj-otel.api.otel :as otel])
  (:import (io.opentelemetry.instrumentation.runtimemetrics.java8 BufferPools
                                                                  Classes
                                                                  Cpu
                                                                  GarbageCollector
                                                                  MemoryPools
                                                                  Threads)))

(defn register-buffer-pools!
  "Registers measurements that generate metrics about buffer pools."
  ([]
   (register-buffer-pools! (otel/get-global-otel!)))
  ([open-telemetry]
   (BufferPools/registerObservers open-telemetry)))

(defn register-classes!
  "Registers measurements that generate metrics about JVM classes."
  ([]
   (register-classes! (otel/get-global-otel!)))
  ([open-telemetry]
   (Classes/registerObservers open-telemetry)))

(defn register-cpu!
  "Registers measurements that generate metrics about the CPU."
  ([]
   (register-cpu! (otel/get-global-otel!)))
  ([open-telemetry]
   (Cpu/registerObservers open-telemetry)))

(defn register-garbage-collector!
  "Registers measurements that generate metrics about the garbage collector."
  ([]
   (register-garbage-collector! (otel/get-global-otel!)))
  ([open-telemetry]
   (GarbageCollector/registerObservers open-telemetry)))

(defn register-memory-pools!
  "Registers measurements that generate metrics about JVM memory pools."
  ([]
   (register-memory-pools! (otel/get-global-otel!)))
  ([open-telemetry]
   (MemoryPools/registerObservers open-telemetry)))

(defn register-threads!
  "Registers measurements that generate metrics about JVM threads."
  ([]
   (register-threads! (otel/get-global-otel!)))
  ([open-telemetry]
   (Threads/registerObservers open-telemetry)))

(defn register!
  "Registers measurements that generate all JVM runtime metrics."
  ([]
   (register! (otel/get-global-otel!)))
  ([open-telemetry]
   (doseq [reg [register-buffer-pools! register-classes! register-cpu! register-garbage-collector!
                register-memory-pools! register-threads!]]
     (reg open-telemetry))))