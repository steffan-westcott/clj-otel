(ns steffan-westcott.clj-otel.api.trace.chan-span
  "Macros for creating spans around core.async channels that are settled with
   a single value, either a result or an exception."
  (:require [clojure.core.async :as async]
            [steffan-westcott.clj-otel.api.trace.span :as span]
            [steffan-westcott.clj-otel.context :as context]
            [steffan-westcott.clj-otel.util :as util]))

#_{:clj-kondo/ignore [:missing-docstring]}
(defmacro ^:no-doc chan-span-binding'
  [[context span-opts] & body]
  `(async/go
     (let [~context (span/new-span!' ~span-opts)
           x#       (async/<! (do
                                ~@body))]
       (when (instance? Throwable x#)
         (span/add-exception! x# {:context ~context}))
       (span/end-span! {:context ~context})
       x#)))

(defmacro chan-span-binding
  "Asynchronously starts a new span, binds `context` to the new context
   containing the span and evaluates `body` which is expected have a channel
   value. The span is ended when the `body` channel is settled (a result or
   exception is put on the channel). Returns a channel that settles with the
   same value."
  [[context span-opts] & body]
  `(let [span-opts# (span/span-opts* ~span-opts ~(:line (meta &form)) ~*file* (util/fn-name))]
     (chan-span-binding' [~context span-opts#]
       ~@body)))

(defmacro async-bound-chan-span
  "Asynchronously starts a new span, sets the bound context to the new context
   containing the span and evaluates `body` which is expected have a channel
   value. The bound context is restored to its original value and the span is
   ended when the `body` channel is settled (a result or exception is put on
   the channel). Returns a channel that settles with the same value."
  [span-opts & body]
  `(let [span-opts# (span/span-opts* ~span-opts ~(:line (meta &form)) ~*file* (util/fn-name))]
     (chan-span-binding' [context# span-opts#]
       (context/bind-context! context#
         ~@body))))
