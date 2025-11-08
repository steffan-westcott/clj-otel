(ns example.word-length-service.system
  "System configuration. See
   https://medium.com/@maciekszajna/reloaded-workflow-out-of-the-box-be6b5f38ea98"
  (:require [example.common.system :refer [closeable] :as common-system]
            [example.word-length-service.env :as env]
            [example.word-length-service.logging :as logging]
            [example.word-length-service.metrics :as metrics]
            [example.word-length-service.server :as server]))


(defonce ^{:doc "Component map of the running system, exposed for inspection in the REPL."} system
  nil)



(defn with-system
  "Evaluates `f` with system map of configured components. The components are
   closed when evaluation completes."
  [f]
  (with-open [config      (closeable (env/set-config!))
              _logging    (closeable (logging/initialize))
              instruments (closeable (metrics/instruments))
              components  (closeable {:instruments @instruments})
              handler     (closeable (server/rebuilding-handler @components))
              server      (closeable (server/server @handler) server/stop-server)]
    (f {:config      @config
        :instruments @instruments
        :components  @components
        :handler     @handler
        :server      @server})))



(defn start!
  "Ensures the system is started and `system` var is set to component map."
  []
  (common-system/start! #'system with-system))



(defn stop!
  "Ensures the system is stopped and `system` var is nil."
  []
  (common-system/stop! #'system))
