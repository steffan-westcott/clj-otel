(ns example.average-service.async-d-explicit.requests
  "Requests to other microservices, Manifold implementation using explicit
   context."
  (:require [clojure.string :as str]
            [example.average-service.env :refer [config]]
            [example.common.async.manifold :as d']
            [hato.client :as client]
            [manifold.deferred :as d]
            [steffan-westcott.clj-otel.api.trace.http :as trace-http]
            [steffan-westcott.clj-otel.api.trace.span :as span]
            [steffan-westcott.clj-otel.context :as context]))


(defn- <client-request
  "Make an asynchronous HTTP request using `hato` and return a deferred of the
   response."
  [client context request]
  (d'/<respond-raise
   (fn [respond raise]
     (let [request (conj request
                         {:async?           true
                          :throw-exceptions false
                          :http-client      client})]

       ;; Manually create a client span with `context` as the parent context.
       ;; Context containing client span is assigned to `context*`. Client span is
       ;; ended when either a response or exception is returned.
       (span/async-span
        (trace-http/client-span-opts request {:parent context})
        (fn [context* respond* raise*]

          (let [;; Propagate context containing client span to remote
                ;; server by injecting headers. This enables span
                ;; correlation to make distributed traces.
                request' (update request :headers merge (context/->headers {:context context*}))]

            (client/request request'
                            (fn [response]

                              ;; Add HTTP response data to the client span.
                              (trace-http/add-client-span-response-data! response
                                                                         {:context context*})

                              (respond* response))
                            (fn [e]

                              ;; Add error information to the client span.
                              (trace-http/add-client-span-response-data!
                               {:io.opentelemetry.api.trace.span.attrs/error-type e}
                               {:context context*})

                              (raise* e)))))
        respond
        raise)))))



(defn <get-sum
  "Get the sum of the nums and return a deferred of the result."
  [{:keys [client]} context nums]
  (let [endpoint  (get-in config [:endpoints :sum-service])
        <response (<client-request client
                                   context
                                   {:method       :get
                                    :url          (str endpoint "/sum")
                                    :query-params {:nums (str/join "," nums)}
                                    :accept       :json
                                    :as           :json})]
    (d/chain' <response
              (fn [{:keys [status body]}]
                (if (= 200 status)
                  (:sum body)
                  (throw (ex-info (str status " HTTP response")
                                  {:http.response/status status
                                   :service/error :service.errors/unexpected-http-response})))))))
