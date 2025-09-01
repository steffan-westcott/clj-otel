(ns steffan-westcott.clj-otel.api.trace.style-span
  "Macros for creating spans around asynchronous code using `async-style`, a
   library that provides higher level utilities built on `core.async`. See
   https://github.com/xadecimal/async-style"
  (:require [com.xadecimal.async-style :as style]
            [steffan-westcott.clj-otel.api.trace.span :as span]
            [steffan-westcott.clj-otel.context :as context]
            [steffan-westcott.clj-otel.util :as util]))

#_{:clj-kondo/ignore [:missing-docstring]}
(defmacro ^:no-doc style-span-binding'
  [[context span-opts] & body]
  `(style/async
     (let [~context (span/new-span!' ~span-opts)]
       (style/finally
         (do
           ~@body)
         (fn [res#]
           (when (style/error? res#)
             (span/add-exception! res# {:context ~context}))
           (span/end-span! {:context ~context}))))))

(defmacro style-span-binding
  "Asynchronously starts a new span, binds `context` to the new context
   containing the span and evaluates `body` which is expected give a channel
   value. The span is ended when the channel is settled (a result or exception
   is put on the channel)."
  [[context span-opts] & body]
  `(let [span-opts# (span/span-opts* ~span-opts ~(:line (meta &form)) ~*file* (util/fn-name))]
     (style-span-binding' [~context span-opts#]
       ~@body)))

(defmacro async-bound-style-span
  "Asynchronously starts a new span, sets the bound context to the new context
   containing the span and evaluates `body` which is expected give a channel
   value. The bound context is restored to its original value and the span is
   ended when the channel is settled (a result or exception is put on the
   channel)."
  [span-opts & body]
  `(let [span-opts# (span/span-opts* ~span-opts ~(:line (meta &form)) ~*file* (util/fn-name))]
     (style-span-binding' [context# span-opts#]
       (context/bind-context! context#
         ~@body))))
