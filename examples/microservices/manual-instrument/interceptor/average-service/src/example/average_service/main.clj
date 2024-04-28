(ns example.average-service.main
  "Main application entry point, used when service is run as a standalone
   application."
  (:require [example.average-service.system :as system]
            [example.common.system.main :as main])
  (:gen-class))


(defn -main
  "Main application entry point."
  [& _args]
  (main/main system/start! system/stop!))
