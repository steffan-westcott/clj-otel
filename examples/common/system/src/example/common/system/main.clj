(ns example.common.system.main
  "Functions for running a system as a standalone application."
  (:require [clojure.tools.logging.readable :as log]
            [example.common.system :as system]))


(defn- log-uncaught-exceptions
  []
  (Thread/setDefaultUncaughtExceptionHandler
   (reify
    Thread$UncaughtExceptionHandler
      (uncaughtException [_ thread e]
        (log/error e "Uncaught exception on thread" (.getName thread))))))



(defn add-shutdown-hook
  "Adds a shutdown hook to evaluate no-arg function f."
  [f]
  (.addShutdownHook (Runtime/getRuntime) (Thread. ^Runnable f)))



(defn- run-system
  [system-var with-system-fn]
  (log/info "Starting system...")
  (system/start! system-var with-system-fn)
  (add-shutdown-hook (fn stop-system []
                       (system/stop! system-var)))
  (log/info "System started"))



(defn main
  "Starts a system, then stops it and shuts down the JVM process when a
   terminate signal is received by the JVM. `system-var` is set to the running
   system map of components passed from `with-system-fn`. `system-var` is
   intended as an aid for remote REPL development and is never read by the main
   code."
  [system-var with-system-fn]
  (try
    (log-uncaught-exceptions)
    (add-shutdown-hook shutdown-agents)
    (run-system system-var with-system-fn)
    (catch Throwable e
      (log/fatal e "Error in main thread")
      (System/exit 1))))
