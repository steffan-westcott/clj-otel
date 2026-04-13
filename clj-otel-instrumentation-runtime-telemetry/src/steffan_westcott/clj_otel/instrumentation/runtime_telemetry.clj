(ns steffan-westcott.clj-otel.instrumentation.runtime-telemetry
  "Functions for registering measurements about the JVM runtime."
  (:require [steffan-westcott.clj-otel.api.otel :as otel])
  (:import io.opentelemetry.api.OpenTelemetry
           io.opentelemetry.instrumentation.runtimetelemetry.RuntimeTelemetry))

(defn create!
  "Create and start JVM runtime telemetry. Listens for select JMX beans and
   (for Java 17+) JFR events. Recording continues until [[close!]] is
   evaluated."
  (^RuntimeTelemetry []
   (create! {}))
  (^RuntimeTelemetry [{:keys [open-telemetry]}]
   (let [^OpenTelemetry otel (or open-telemetry (otel/get-default-otel!))
         builder (RuntimeTelemetry/builder otel)]
     (.build builder))))

(defn close!
  "Stop recording JVM runtime telemetry."
  [^RuntimeTelemetry runtime-telemetry]
  (.close runtime-telemetry))
