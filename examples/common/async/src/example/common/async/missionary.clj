(ns example.common.async.missionary
  "Common utilities for Missionary."
  (:require [clojure.core.async :as async]
            [example.common.async.response :as common-response]
            [steffan-westcott.clj-otel.api.trace.span :as span]
            [steffan-westcott.clj-otel.api.trace.task-span :as task-span]
            [steffan-westcott.clj-otel.util :as util]))


(defmacro route-span-binding
  "Asynchronously starts a new span around processing for a route with Pedestal
   context `ctx`, binding `context` to the new context containing the span.
   The span is created as a child of the server span. `body` is expected to
   give a task which will contain the response."
  [[context ctx] & body]
  `(let [span-opts# (span/span-opts* {:name   "Handling route"
                                      :parent (:io.opentelemetry/server-span-context ~ctx)}
                                     ~(:line (meta &form))
                                     ~*file*
                                     (util/fn-name))]
     (task-span/task-span-binding' [~context span-opts#]
       ~@body)))



(defn <assoc-response
  "Takes a task of an HTTP response and returns a channel containing ctx
   with the response attached. If the task yields an exception, it is first
   transformed to a response."
  [task ctx]
  (let [ch (async/promise-chan)
        f  #(async/put! ch (assoc ctx :response %))]
    (task f (comp f common-response/exception-response))
    ch))
