(ns example.word-length-service.system
  "System configuration. See
   https://medium.com/@maciekszajna/reloaded-workflow-out-of-the-box-be6b5f38ea98"
  (:require [example.common.system :refer [closeable]]
            [example.word-length-service.env :as env]
            [example.word-length-service.metrics :as metrics]
            [example.word-length-service.server :as server]
            [nrepl.server :as nrepl]))


(defn with-system
  "Evaluates `f` with system map of configured components. The components are
   closed when evaluation completes."
  [f]
  (with-open [config      (closeable (env/config))
              instruments (closeable (metrics/instruments))
              components  (closeable {:config      @config
                                      :instruments @instruments})
              handler     (closeable (server/rebuilding-handler @components))
              server      (closeable (server/server @config @handler) server/stop-server)
              nrepl       (nrepl/start-server (:nrepl @config))]
    (f {:config      @config
        :instruments @instruments
        :components  @components
        :handler     @handler
        :server      @server
        :nrepl       nrepl})))
