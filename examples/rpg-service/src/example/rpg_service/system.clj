(ns example.rpg-service.system
  "System configuration. See
   https://medium.com/@maciekszajna/reloaded-workflow-out-of-the-box-be6b5f38ea98"
  (:require [example.common.system :refer [closeable]]
            [example.rpg-service.db :as db]
            [example.rpg-service.env :as env]
            [example.rpg-service.metrics :as metrics]
            [example.rpg-service.server :as server]))


(defn with-system
  "Evaluates `f` with system map of configured components. The components are
   closed when evaluation completes."
  [f]
  (with-open [config      (closeable (env/set-config!))
              instruments (closeable (metrics/instruments))
              datasource  (closeable (db/datasource (:datasource @config)) db/stop-datasource)
              eql-db      (closeable (db/eql-db @datasource))
              components  (closeable {:instruments @instruments
                                      :eql-db      @eql-db})
              handler     (closeable (server/rebuilding-handler @components))
              server      (closeable (server/server (:jetty @config) @handler) server/stop-server)]
    (f {:config      @config
        :instruments @instruments
        :datasource  @datasource
        :eql-db      @eql-db
        :components  @components
        :handler     @handler
        :server      @server})))
