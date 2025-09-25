(ns example.common.async.auspex
  "Common utilities for auspex."
  (:require [clojure.core.async :as async]
            [example.common.async.response :as common-response]
            [qbits.auspex :as aus]
            [steffan-westcott.clj-otel.api.trace.span :as span]
            [steffan-westcott.clj-otel.util :as util]))


(defn cf->respond-raise
  "When `^CompletableFuture <cf` completes, invokes callback `respond` or
   `raise`."
  [<cf respond raise]
  (aus/when-complete <cf
                     (bound-fn [response e]
                       (if e
                         (raise (aus/ex-unwrap e))
                         (respond response)))))


(defn <cf-response
  "Returns a channel that will contain ctx with HTTP response attached. The
   response is taken from the completion of `^CompletableFuture cf`. If `cf`
   completes exceptionally, the exception is first transformed to a response."
  [cf ctx]
  (let [ch (async/promise-chan)]
    (aus/when-complete cf
                       (fn [response e]
                         (async/put!
                          ch
                          (assoc ctx
                                 :response
                                 (if e
                                   (common-response/exception-response (aus/ex-unwrap e))
                                   response)))))
    ch))


(defmacro route-span-binding
  "Asynchronously starts a new span around processing for a route with Pedestal
   context `ctx`, binding `context` to the new context containing the span.
   The span is created as a child of the server span. `body` is expected to
   give a `CompletableFuture` which will contain the response."
  [[context ctx] & body]
  `(let [span-opts# (span/span-opts* {:name   "Handling route"
                                      :parent (:io.opentelemetry/server-span-context ~ctx)}
                                     ~(:line (meta &form))
                                     ~(:column (meta &form))
                                     ~*file*
                                     (util/fn-name))]
     (span/cf-span-binding [~context span-opts#]
       ~@body)))
