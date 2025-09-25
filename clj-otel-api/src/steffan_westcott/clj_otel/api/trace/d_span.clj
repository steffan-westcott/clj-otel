(ns steffan-westcott.clj-otel.api.trace.d-span
  "Macros for creating spans around Manifold deferreds. To use this namespace,
   add dependency `manifold/manifold`."
  (:require [manifold.deferred :as d]
            [steffan-westcott.clj-otel.api.trace.span :as span]
            [steffan-westcott.clj-otel.context :as context]
            [steffan-westcott.clj-otel.util :as util]))

#_{:clj-kondo/ignore [:missing-docstring]}
(defmacro ^:no-doc d-span-binding'
  [[context span-opts] & body]
  `(d/chain' (d/future
               (span/new-span!' ~span-opts))
             (fn [context#]
               (try
                 (-> (let [~context context#]
                       ~@body)
                     (d/on-realized identity #(span/add-exception! % {:context context#}))
                     (d/finally' #(span/end-span! {:context context#})))
                 (catch Throwable e#
                   (span/add-exception! e# {:context context#})
                   (span/end-span! {:context context#})
                   (throw e#))))))

(defmacro d-span-binding
  "Asynchronously starts a new span, binds `context` to the new context
   containing the span and evaluates `body` which is expected have a deferred
   value. The span is ended when the `body` deferred yields a value. Returns a
   deferred that yields the same value."
  [[context span-opts] & body]
  `(let [span-opts# (span/span-opts* ~span-opts
                                     ~(:line (meta &form))
                                     ~(:column (meta &form))
                                     ~*file*
                                     (util/fn-name))]
     (d-span-binding' [~context span-opts#]
       ~@body)))

(defmacro async-bound-d-span
  "Asynchronously starts a new span, sets the bound context to the new context
   containing the span and evaluates `body` which is expected have a deferred
   value. The bound context is restored to its original value and the span is
   ended when the `body` deferred yields a value. Returns a deferred that
   yields the same value."
  [span-opts & body]
  `(let [span-opts# (span/span-opts* ~span-opts
                                     ~(:line (meta &form))
                                     ~(:column (meta &form))
                                     ~*file*
                                     (util/fn-name))]
     (d-span-binding' [context# span-opts#]
       (context/bind-context! context#
         ~@body))))
