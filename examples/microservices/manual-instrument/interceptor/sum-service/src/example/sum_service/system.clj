;!zprint {:style [:respect-nl] :width 140}

(ns example.sum-service.system
  "System state and configuration. See
   https://medium.com/@maciekszajna/reloaded-workflow-out-of-the-box-be6b5f38ea98"
  (:require [example.common.system :refer [closeable] :as common-system]
            [example.sum-service.env :as env]
            [example.sum-service.logging :as logging]
            [example.sum-service.metrics :as metrics]
            [example.sum-service.server :as server]
            [steffan-westcott.clj-otel.instrumentation.runtime-telemetry-java17 :as runtime-telemetry]
            [steffan-westcott.clj-otel.sdk.autoconfigure :as autoconfig]))


(defonce ^{:doc "Component map of the running system, exposed for inspection in the REPL."} system
  nil)



(defn with-system
  "Evaluates `f` with system map of configured components. The components are
   closed when evaluation completes."
  [f]
  (with-open [config          (closeable (env/set-config!))
              otel-sdk        (closeable (autoconfig/init-otel-sdk!)) ; registers its own shutdown hook for closing
              runtime-metrics (runtime-telemetry/register!)
              _logging        (closeable (logging/install! @otel-sdk))
              instruments     (closeable (metrics/instruments))
              components      (closeable {:instruments @instruments})
              connector       (closeable (server/connector @components) server/stop-connector)]
    (f {:config          @config
        :otel-sdk        @otel-sdk
        :runtime-metrics runtime-metrics
        :instruments     @instruments
        :components      @components
        :connector       @connector})))



(defn start!
  "Ensures the system is started and `system` var is set to component map."
  []
  (common-system/start! #'system with-system))



(defn stop!
  "Ensures the system is stopped and `system` var is nil."
  []
  (common-system/stop! #'system))
