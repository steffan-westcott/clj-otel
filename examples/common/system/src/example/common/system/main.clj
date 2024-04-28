(ns example.common.system.main
  "Functions for running a system as a standalone application."
  (:require [clojure.tools.logging.readable :as log]))


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



(defn main
  "Starts a system, then stops it and shuts down the JVM process when a
   terminate signal is received by the JVM."
  [start-system! stop-system!]
  (try
    (log-uncaught-exceptions)
    (add-shutdown-hook shutdown-agents)
    (log/info "Starting system...")
    (start-system!)
    (add-shutdown-hook stop-system!)
    (log/info "System started")
    (catch Throwable e
      (log/fatal e "Error in main thread")
      (System/exit 1))))