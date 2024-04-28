(ns example.average-load-gen.system
  "System state and configuration. See
   https://medium.com/@maciekszajna/reloaded-workflow-out-of-the-box-be6b5f38ea98"
  (:require [example.average-load-gen.env :as env]
            [example.average-load-gen.load :as load]
            [example.common.load-gen.client :as client]
            [example.common.system :refer [closeable] :as common-system]))


(defonce ^{:doc "Component map of the running system, exposed for inspection in the REPL."} system
  nil)



(defn with-system
  "Evaluates `f` with system map of configured components. The components are
   closed when evaluation completes."
  [f]
  (with-open [config   (closeable (env/set-config!))
              conn-mgr (closeable (client/connection-manager) client/stop-connection-manager)
              client   (client/client @conn-mgr)
              load     (closeable (load/start-load @conn-mgr client) future-cancel)]
    (f {:config   @config
        :conn-mgr @conn-mgr
        :client   client
        :load     @load})))



(defn start!
  "Ensures the system is started and `system` var is set to component map."
  []
  (common-system/start! #'system with-system))



(defn stop!
  "Ensures the system is stopped and `system` var is nil."
  []
  (common-system/stop! #'system))
