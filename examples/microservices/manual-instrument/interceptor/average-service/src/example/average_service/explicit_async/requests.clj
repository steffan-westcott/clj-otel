(ns example.average-service.explicit-async.requests
  "Requests to other microservices, explicit async implementation."
  (:require [clojure.core.async :as async]
            [clojure.string :as str]
            [example.average-service.env :refer [config]]
            [example.common.core-async.utils :as async']
            [hato.client :as client]
            [steffan-westcott.clj-otel.api.trace.http :as trace-http]
            [steffan-westcott.clj-otel.api.trace.span :as span]
            [steffan-westcott.clj-otel.context :as context]))


(defn client-request
  "Make an asynchronous HTTP request using `hato`."
  [client context request respond raise]

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
                           (trace-http/add-client-span-response-data! response {:context context*})

                           (respond* response))
                         (fn [e]

                           ;; Add error information to the client span.
                           (trace-http/add-client-span-response-data!
                            {:io.opentelemetry.api.trace.span.attrs/error-type e}
                            {:context context*})
                           (raise* e)))))
     respond
     raise)))



(defn <client-request
  "Make an asynchronous HTTP request and return a channel of the response."
  [client context request]
  (let [<ch    (async/chan)
        put-ch #(async/put! <ch %)]
    (client-request client context request put-ch put-ch)
    <ch))



(defn <get-sum
  "Get the sum of the nums and return a channel of the result."
  [{:keys [client]} context nums]
  (let [endpoint  (get-in config [:endpoints :sum-service])
        <response (<client-request client
                                   context
                                   {:method       :get
                                    :url          (str endpoint "/sum")
                                    :query-params {:nums (str/join "," nums)}
                                    :accept       :json
                                    :as           :json})]
    (async'/go-try
      (let [response (async'/<? <response)
            {:keys [status body]} response]
        (if (= 200 status)
          (:sum body)
          (throw (ex-info (str status " HTTP response")
                          {:http.response/status status
                           :service/error        :service.errors/unexpected-http-response})))))))
