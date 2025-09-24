(ns example.planet-service.system
  "System state and configuration. See
   https://medium.com/@maciekszajna/reloaded-workflow-out-of-the-box-be6b5f38ea98"
  (:require [example.common.system :refer [closeable] :as common-system]
            [example.planet-service.env :as env]
            [example.planet-service.metrics :as metrics]
            [example.planet-service.server :as server]))


(defonce ^{:doc "Component map of the running system, exposed for inspection in the REPL."} system
  nil)



(defn with-system
  "Evaluates `f` with system map of configured components. The components are
   closed when evaluation completes."
  [f]
  (with-open [config      (closeable (env/set-config!))
              instruments (closeable (metrics/instruments))
              components  (closeable {:instruments @instruments})
              connector   (closeable (server/connector @components) server/stop-connector)]
    (f {:config      @config
        :instruments @instruments
        :components  @components
        :connector   @connector})))



(defn start!
  "Ensures the system is started and `system` var is set to component map."
  []
  (common-system/start! #'system with-system))



(defn stop!
  "Ensures the system is stopped and `system` var is nil."
  []
  (common-system/stop! #'system))
