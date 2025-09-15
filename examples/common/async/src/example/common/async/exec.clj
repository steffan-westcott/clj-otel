(ns example.common.async.exec
  "Executors for use by the application."
  (:import (java.util.concurrent Executors ThreadFactory)
           (java.util.concurrent.atomic AtomicLong)))

(defn- thread-factory
  [prefix]
  (let [n (AtomicLong. 0)]
    (reify
     ThreadFactory
       (newThread [_ runnable]
         (doto (Thread. runnable (str prefix (.getAndIncrement n)))
           (.setDaemon true))))))

(def cpu
  "Executor for use by CPU intensive tasks that never block."
  (Executors/newFixedThreadPool (.availableProcessors (Runtime/getRuntime))
                                (thread-factory "app-cpu-")))

(def block
  "Executor for use by tasks that may block."
  (Executors/newCachedThreadPool (thread-factory "app-block-")))
