(ns example.puzzle-load-gen.repl
  "Functions for operating load gen via a local or remote REPL."
  (:require [example.common.system.main :as common-main]
            [example.puzzle-load-gen.env :as env]
            [example.puzzle-load-gen.main :as main]
            [example.puzzle-load-gen.system :as system]
            [nrepl.server :as nrepl]))


(defn- run-nrepl
  []
  (let [server (nrepl/start-server (:nrepl (env/read-config)))]
    (common-main/add-shutdown-hook (fn stop-nrepl []
                                     (nrepl/stop-server server)))))



(defn -main
  "Starts the system and an embedded nREPL server for remote REPL access.
   They are stopped when a terminate signal is received by the JVM.
   Intended for use by `clojure` command."
  [& opts]
  (run-nrepl)
  (main/-main opts))



(defn start!
  "Ensures the system is started. Intended for use in local or remote REPL."
  []
  (system/start!))



(defn stop!
  "Ensure the system is stopped. Intended for use in local or remote REPL."
  []
  (system/stop!))



(comment
  (stop!)
  (start!)
  ;
)
