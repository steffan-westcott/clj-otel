(ns example.common.async.async-style
  "Common utilities for async-style."
  (:require [clojure.core.async :as async]
            [com.xadecimal.async-style :as style]
            [example.common.async.response :as common-response]
            [steffan-westcott.clj-otel.api.trace.chan-span :as chan-span]
            [steffan-westcott.clj-otel.api.trace.span :as span]
            [steffan-westcott.clj-otel.context :as context]
            [steffan-westcott.clj-otel.util :as util]))


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
     (chan-span/chan-span-binding' [~context span-opts#]
       ~@body)))



(defmacro route-bound-span
  "Asynchronously starts a new span around processing for a route and sets the
   bound context to the new context containing the span. `body` is expected to
   give a channel which will contain the response."
  [& body]
  `(let [span-opts# (span/span-opts* "Handling route" ~(:line (meta &form)) ~*file* (util/fn-name))]
     (chan-span/chan-span-binding' [context# span-opts#]
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
