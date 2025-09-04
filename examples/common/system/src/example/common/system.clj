(ns example.common.system
  "Functions for configuring, starting and stopping systems of components. A
   system is configured by a user-provided function that uses
   `clojure.core/with-open`. See
   https://medium.com/@maciekszajna/reloaded-workflow-out-of-the-box-be6b5f38ea98"
  (:import (clojure.lang IDeref)
           (java.io Closeable)))


(defn closeable
  "Wrap a value with support for `Closeable`."
  (^Closeable [value] (closeable value identity))
  (^Closeable [value close]
   (reify
    IDeref
      (deref [_]
        value)
    Closeable
      (close [_]
        (close value)))))



(defn- maybe-throw
  [x]
  (if (instance? Throwable x)
    (throw x)
    x))



(defn- run
  "Starts evaluation of `(with-system-fn f)` in another thread and returns
   `system`, a map of configured system components. It is expected that
   `with-system-fn` evaluates `(f system)`, most easily achieved using
   `with-open`. The started thread is blocked while the system runs (in other
   threads). `system` includes metadata `::stop-fn`, which when evaluated will
   close the system components and terminate the thread."
  [with-system-fn]
  (let [system-p (promise)
        stop-p   (promise)
        runner   (future
                   (try
                     (with-system-fn (fn wait [system]
                                       (deliver system-p system)
                                       @stop-p))
                     (catch Throwable e
                       (deliver system-p e)
                       (throw e))))
        stop-fn  (fn []
                   (deliver stop-p nil)
                   @runner)]
    (vary-meta (maybe-throw @system-p) assoc ::stop-fn stop-fn)))



(defn start!
  "Ensures system is started and `system-var` is set to map of system
   components passed from `with-system-fn`."
  [system-var with-system-fn]
  (alter-var-root system-var
                  (fn [system]
                    (or system (run with-system-fn))))
  :started)



(defn stop!
  "Ensures system is stopped and `system-var` is nil."
  [system-var]
  (alter-var-root system-var
                  (fn [system]
                    (when system
                      ((::stop-fn (meta system))))
                    nil))
  :stopped)
