(ns example.puzzle-load-gen.system
  "System state and configuration. See
   https://medium.com/@maciekszajna/reloaded-workflow-out-of-the-box-be6b5f38ea98"
  (:require [example.common.load-gen.client :as client]
            [example.common.system :refer [closeable] :as common-system]
            [example.puzzle-load-gen.env :as env]
            [example.puzzle-load-gen.load :as load]))


(defonce ^{:doc "Component map of the running system, exposed for inspection in the REPL."} system
  nil)



(defn with-system
  "Evaluates `f` with system map of configured components. The components are
   closed when evaluation completes."
  [f]
  (with-open [config (closeable (env/set-config!))
              client (client/client)
              load   (closeable (load/start-load client) future-cancel)]
    (f {:config @config
        :client client
        :load   @load})))



(defn start!
  "Ensures the system is started and `system` var is set to component map."
  []
  (common-system/start! #'system with-system))



(defn stop!
  "Ensures the system is stopped and `system` var is nil."
  []
  (common-system/stop! #'system))
