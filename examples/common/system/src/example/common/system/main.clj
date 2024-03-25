(ns example.common.system.main
  "Functions for running a system as a standalone Java application."
  (:require [clojure.tools.logging.readable :as log]
            [example.common.system :as system]))


(defn- maybe-throw
  [x]
  (if (instance? Throwable x)
    (throw x)
    x))



(defn- set-default-uncaught-exception-handler
  []
  (Thread/setDefaultUncaughtExceptionHandler
   (reify
    Thread$UncaughtExceptionHandler
      (uncaughtException [_ thread e]
        (log/error e "Uncaught exception on thread" (.getName thread))))))



(defn- start-system
  [system-var with-system-fn]
  (log/info "Starting system...")
  (let [[system-p stop-fn] (system/run with-system-fn)
        sys (maybe-throw @system-p)]
    (alter-var-root system-var (constantly sys))
    (.addShutdownHook (Runtime/getRuntime)
                      (Thread. (fn []
                                 (try
                                   (log/info "Stopping system...")
                                   (stop-fn)
                                   (log/info "System stopped")
                                   (catch Throwable e
                                     (log/error e "System error while stopping")))
                                 (alter-var-root system-var (constantly nil))
                                 (shutdown-agents))))
    (log/info "System started")))



(defn main
  "Starts a system, then stops it and shuts down the JVM process when a
   terminate signal is received by the JVM. `system-var` is set to the running
   system map of components passed from `with-system-fn`. `system-var` is
   intended as an aid for remote REPL development and is never read by the main
   code."
  [system-var with-system-fn]
  (try
    (set-default-uncaught-exception-handler)
    (start-system system-var with-system-fn)
    (catch Throwable e
      (log/fatal e "Error in main thread")
      (System/exit 1))))
