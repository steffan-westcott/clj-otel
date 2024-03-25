(ns example.common.system
  "Functions for configuring, starting and stopping systems of components. A
   system is configured by a user-provided function that uses
   `clojure.core/with-open`. The system is run using functions in namespaces
   `example.common.system.main` and `example.common.system.repl`. See
   https://medium.com/@maciekszajna/reloaded-workflow-out-of-the-box-be6b5f38ea98"
  (:import (clojure.lang IDeref)
           (java.io Closeable)))


(defn closeable
  "Wrap a value with support for `Closeable`."
  ([value] (closeable value identity))
  ([value close]
   (reify
    IDeref
      (deref [_]
        value)
    Closeable
      (close [_]
        (close value)))))



(defn run
  "Starts evaluation of `(with-system-fn f)` in another thread and returns
   `[system-p stop-fn]`. It is expected that `with-system-fn` evaluates
   `(f system)` in the context of a system of configured components, most
   easily achieved using `with-open`. `system-p` is a promise of the map
   `system` or an exception. The started thread is blocked while the system
   runs (in other threads). When `stop-fn` is evaluated, the system components
   are closed and the thread terminates."
  [with-system-fn]
  (let [system-p (promise)
        stop-p   (promise)
        runner   (future (try
                           (with-system-fn (fn wait [system]
                                             (deliver system-p system)
                                             @stop-p))
                           (catch Throwable e
                             (deliver system-p e)
                             (throw e))))
        stop-fn  (fn []
                   (deliver stop-p nil)
                   @runner)]
    [system-p stop-fn]))
