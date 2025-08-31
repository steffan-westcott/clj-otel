(ns example.common.async.async-style
  "Common utilities for async-style."
  (:require [clojure.core.async :as async]
            [com.xadecimal.async-style :as style]
            [example.common.async.response :as common-response]
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



(defn <respond-raise
  "Calls `f` with function arguments `respond` and `raise`, then returns
   channel with value passed by either function."
  [f]
  (let [<ch     (async/promise-chan)
        respond #(async/put! <ch %)
        raise   respond]
    (f respond raise)
    <ch))



(defmacro route-span-binding
  "Asynchronously starts a new span around processing for a route with Pedestal
   context `ctx`, binding `context` to the new context containing the span.
   The span is created as a child of the server span. `body` is expected to
   give a channel which will contain the response."
  [[context ctx] & body]
  `(let [span-opts# (span/span-opts* {:name   "Handling route"
                                      :parent (:io.opentelemetry/server-span-context ~ctx)}
                                     ~(:line (meta &form))
                                     ~*file*
                                     (util/fn-name))]
     (style-span-binding' [~context span-opts#]
       ~@body)))



(defmacro route-bound-span
  "Asynchronously starts a new span around processing for a route and sets the
   bound context to the new context containing the span. `body` is expected to
   give a channel which will contain the response."
  [& body]
  `(let [span-opts# (span/span-opts* "Handling route" ~(:line (meta &form)) ~*file* (util/fn-name))]
     (style-span-binding' [context# span-opts#]
       (context/bind-context! context#
         ~@body))))



(defn <assoc-response
  "Takes a channel containing an HTTP response and returns a channel of ctx
   with the response attached. If the channel contains an exception, it is
   first transformed to a response."
  [<ch ctx]
  (-> <ch
      (style/catch common-response/exception-response)
      (style/then #(assoc ctx :response %))))
