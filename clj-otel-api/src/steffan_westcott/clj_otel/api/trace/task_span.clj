(ns steffan-westcott.clj-otel.api.trace.task-span
  "Macros for creating spans around Missionary tasks. To use this namespace,
   add dependency `missionary/missionary`."
  (:require [missionary.core :as m]
            [steffan-westcott.clj-otel.api.trace.span :as span]
            [steffan-westcott.clj-otel.util :as util]))


#_{:clj-kondo/ignore [:missing-docstring]}
(defmacro ^:no-doc task-span-binding'
  [[context span-opts] & body]
  `(m/sp
     (let [~context (span/new-span!' ~span-opts)]
       (try
         (m/? (do
                ~@body))
         (catch Throwable e#
           (span/add-exception! e# {:context ~context})
           (throw e#))
         (finally
           (span/end-span! {:context ~context}))))))

(defmacro task-span-binding
  "Asynchronously starts a new span, binds `context` to the new context
   containing the span and evaluates `body` which is expected have a task
   value. The span is ended when the `body` task yields a value. Returns a
   task that yields the same value."
  [[context span-opts] & body]
  `(let [span-opts# (span/span-opts* ~span-opts ~(:line (meta &form)) ~*file* (util/fn-name))]
     (task-span-binding' [~context span-opts#]
       ~@body)))
