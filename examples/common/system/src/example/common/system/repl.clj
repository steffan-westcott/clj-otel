(ns example.common.system.repl
  "Functions for running a system in the REPL."
  (:require [example.common.system :as system]))


(defn- maybe-throw
  [x]
  (if (instance? Throwable x)
    (throw x)
    x))



(defn start!
  "Starts a system and sets `system-var` to map of system components passed
   from `with-system-fn`."
  [system-var with-system-fn]
  (let [[system-p stop-fn] (system/run with-system-fn)
        sys (with-meta (maybe-throw @system-p) {:stop-fn stop-fn})]
    (alter-var-root system-var (constantly sys)))
  :started)



(defn stop!
  "Stops a started system, then set `system-var` to nil."
  [system-var]
  ((:stop-fn (meta @system-var)))
  (alter-var-root system-var (constantly nil))
  :stopped)
