(ns example.common.async.manifold
  "Common utilities for Manifold."
  (:require [clojure.core.async :as async]
            [example.common.async.response :as common-response]
            [manifold.deferred :as d]
            [steffan-westcott.clj-otel.api.trace.d-span :as d-span]
            [steffan-westcott.clj-otel.api.trace.span :as span]
            [steffan-westcott.clj-otel.util :as util]))


(defn <respond-raise
  "Calls `f` with function arguments `respond` and `raise`, then returns
   deferred with value passed by either function."
  [f]
  (let [<d (d/deferred)]
    (f #(d/success! <d %) #(d/error! <d %))
    <d))



(defn <d-response
  "Returns a channel that will contain ctx with HTTP response attached. The
   response is taken from the deferred `d`. If `d` yields an exception value,
   the exception is first transformed to a response."
  [d ctx]
  (let [ch (async/promise-chan)
        f  #(async/put! ch (assoc ctx :response %))]
    (d/on-realized d f (comp f common-response/exception-response))
    ch))


(defmacro route-span-binding
  "Asynchronously starts a new span around processing for a route with Pedestal
   context `ctx`, binding `context` to the new context containing the span.
   The span is created as a child of the server span. `body` is expected to
   give a deferred which will contain the response."
  [[context ctx] & body]
  `(let [span-opts# (span/span-opts* {:name   "Handling route"
                                      :parent (:io.opentelemetry/server-span-context ~ctx)}
                                     ~(:line (meta &form))
                                     ~(:column (meta &form))
                                     ~*file*
                                     (util/fn-name))]
     (d-span/d-span-binding [~context span-opts#]
       ~@body)))
