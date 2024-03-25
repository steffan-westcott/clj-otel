(ns example.sentence-summary-service.system
  "System configuration. See
   https://medium.com/@maciekszajna/reloaded-workflow-out-of-the-box-be6b5f38ea98"
  (:require [example.common.system :refer [closeable]]
            [example.sentence-summary-service.client :as client]
            [example.sentence-summary-service.env :as env]
            [example.sentence-summary-service.metrics :as metrics]
            [example.sentence-summary-service.server :as server]
            [nrepl.server :as nrepl]))


(defn with-system
  "Evaluates `f` with system map of configured components. The components are
   closed when evaluation completes."
  [f]
  (with-open [config      (closeable (env/config))
              instruments (closeable (metrics/instruments))
              conn-mgr    (closeable (client/connection-manager @config)
                                     client/stop-connection-manager)
              client      (closeable (client/client @config @conn-mgr))
              components  (closeable {:config      @config
                                      :instruments @instruments
                                      :conn-mgr    @conn-mgr
                                      :client      @client})
              handler     (closeable (server/rebuilding-handler @components))
              server      (closeable (server/server @config @handler) server/stop-server)
              nrepl       (nrepl/start-server (:nrepl @config))]
    (f {:config      @config
        :instruments @instruments
        :conn-mgr    @conn-mgr
        :client      @client
        :components  @components
        :handler     @handler
        :server      @server
        :nrepl       nrepl})))
