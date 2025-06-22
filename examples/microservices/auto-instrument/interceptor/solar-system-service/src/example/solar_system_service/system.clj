(ns example.solar-system-service.system
  "System state and configuration. See
   https://medium.com/@maciekszajna/reloaded-workflow-out-of-the-box-be6b5f38ea98"
  (:require [example.common.system :refer [closeable] :as common-system]
            [example.solar-system-service.client :as client]
            [example.solar-system-service.env :as env]
            [example.solar-system-service.metrics :as metrics]
            [example.solar-system-service.server :as server]))


(defonce ^{:doc "Component map of the running system, exposed for inspection in the REPL."} system
  nil)



(defn with-system
  "Evaluates `f` with system map of configured components. The components are
   closed when evaluation completes."
  [f]
  (with-open [config      (closeable (env/set-config!))
              instruments (closeable (metrics/instruments))
              client      (client/client)
              components  (closeable {:instruments @instruments
                                      :client      client})
              service-map (closeable (server/service-map @components))
              server      (closeable (server/server @service-map) server/stop-server)]
    (f {:config      @config
        :instruments @instruments
        :client      client
        :components  @components
        :service-map @service-map
        :server      @server})))



(defn start!
  "Ensures the system is started and `system` var is set to component map."
  []
  (common-system/start! #'system with-system))



(defn stop!
  "Ensures the system is stopped and `system` var is nil."
  []
  (common-system/stop! #'system))
