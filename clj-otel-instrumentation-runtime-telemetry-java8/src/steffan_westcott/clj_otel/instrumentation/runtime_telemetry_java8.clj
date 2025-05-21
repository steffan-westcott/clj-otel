(ns steffan-westcott.clj-otel.instrumentation.runtime-telemetry-java8
  "Functions for registering measurements about the JVM runtime on Java 8+."
  (:require [steffan-westcott.clj-otel.api.otel :as otel])
  (:import (io.opentelemetry.instrumentation.runtimemetrics.java8 Classes
                                                                  Cpu
                                                                  GarbageCollector
                                                                  MemoryPools
                                                                  Threads)
           (java.lang AutoCloseable)
           (java.util List)))

(defn register-classes!
  "Registers measurements that generate metrics about JVM classes and returns a
   list of AutoCloseable."
  (^List []
   (register-classes! (otel/get-default-otel!)))
  (^List [open-telemetry]
   (Classes/registerObservers open-telemetry)))

(defn register-cpu!
  "Registers measurements that generate metrics about the CPU and returns a
   list of AutoCloseable."
  (^List []
   (register-cpu! (otel/get-default-otel!)))
  (^List [open-telemetry]
   (Cpu/registerObservers open-telemetry)))

(defn register-garbage-collector!
  "Registers measurements that generate metrics about the garbage collector and
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

(defn register-memory-pools!
  "Registers measurements that generate metrics about JVM memory pools and
   returns a list of AutoCloseable."
  (^List []
   (register-memory-pools! (otel/get-default-otel!)))
  (^List [open-telemetry]
   (MemoryPools/registerObservers open-telemetry)))

(defn register-threads!
  "Registers measurements that generate metrics about JVM threads and returns a
   list of AutoCloseable."
  (^List []
   (register-threads! (otel/get-default-otel!)))
  (^List [open-telemetry]
   (Threads/registerObservers open-telemetry)))

(defn register!
  "Registers measurements that generate all JVM runtime metrics and returns a
   collection of AutoCloseable."
  ([]
   (register! (otel/get-default-otel!)))
  ([open-telemetry]
   (reduce #(into %1 (%2 open-telemetry))
           []
           [register-classes! register-cpu! register-garbage-collector! register-memory-pools!
            register-threads!])))

(defn close!
  "Closes each of a collection of AutoCloseable."
  [coll-closeable]
  (doseq [^AutoCloseable c coll-closeable]
    (.close c)))