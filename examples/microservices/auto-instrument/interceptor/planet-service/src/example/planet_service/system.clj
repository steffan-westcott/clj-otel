(ns example.planet-service.system
  "System configuration. See
   https://medium.com/@maciekszajna/reloaded-workflow-out-of-the-box-be6b5f38ea98"
  (:require [example.common.system :refer [closeable]]
            [example.planet-service.env :as env]
            [example.planet-service.metrics :as metrics]
            [example.planet-service.server :as server]
            [nrepl.server :as nrepl]))


(defn with-system
  "Evaluates `f` with system map of configured components. The components are
   closed when evaluation completes."
  [f]
  (with-open [config      (closeable (env/config))
              instruments (closeable (metrics/instruments))
              components  (closeable {:config      @config
                                      :instruments @instruments})
              service-map (closeable (server/service-map @components))
              server      (closeable (server/server @service-map) server/stop-server)
              nrepl       (nrepl/start-server (:nrepl @config))]
    (f {:config      @config
        :instruments @instruments
        :components  @components
        :service-map @service-map
        :server      @server
        :nrepl       nrepl})))
