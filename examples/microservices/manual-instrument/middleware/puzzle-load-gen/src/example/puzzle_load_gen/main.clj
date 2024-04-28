(ns example.puzzle-load-gen.main
  "Main application entry point, used when service is run as a standalone
   application."
  (:require [example.common.system.main :as main]
            [example.puzzle-load-gen.system :as system])
  (:gen-class))


(defn -main
  "Main application entry point."
  [& _args]
  (main/main system/start! system/stop!))
