;!zprint {:style [:respect-nl] :width 140}

(ns example.average-service.system
  "System configuration. See
   https://medium.com/@maciekszajna/reloaded-workflow-out-of-the-box-be6b5f38ea98"
  (:require [example.average-service.client :as client]
            [example.average-service.env :as env]
            [example.average-service.metrics :as metrics]
            [example.average-service.server :as server]
            [example.common.system :refer [closeable]]
            [nrepl.server :as nrepl]
            [steffan-westcott.clj-otel.instrumentation.runtime-telemetry-java17 :as runtime-telemetry]
            [steffan-westcott.clj-otel.sdk.autoconfigure :as autoconfig]))


(defn with-system
  "Evaluates `f` with system map of configured components. The components are
   closed when evaluation completes."
  [f]
  (with-open [config          (closeable (env/config))
              otel-sdk        (closeable (autoconfig/init-otel-sdk!)) ; registers its own shutdown hook for closing
              runtime-metrics (runtime-telemetry/register!)
              instruments     (closeable (metrics/instruments))
              conn-mgr        (closeable (client/connection-manager @config)
                                         client/stop-connection-manager)
              client          (closeable (client/client @config @conn-mgr))
              components      (closeable {:config      @config
                                          :instruments @instruments
                                          :conn-mgr    @conn-mgr
                                          :client      @client})
              service-map     (closeable (server/service-map @components))
              server          (closeable (server/server @service-map) server/stop-server)
              nrepl           (nrepl/start-server (:nrepl @config))]
    (f {:config          @config
        :otel-sdk        @otel-sdk
        :runtime-metrics runtime-metrics
        :instruments     @instruments
        :conn-mgr        @conn-mgr
        :client          @client
        :components      @components
        :service-map     @service-map
        :server          @server
        :nrepl           nrepl})))
