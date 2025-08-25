(ns example.average-service.bound-async.requests
  "Requests to other microservices, bound async implementation."
  (:require [clojure.string :as str]
            [com.xadecimal.async-style :as style]
            [example.average-service.env :refer [config]]
            [example.common.async.async-style :as style']
            [hato.client :as client]
            [steffan-westcott.clj-otel.api.trace.http :as trace-http]
            [steffan-westcott.clj-otel.api.trace.span :as span]
            [steffan-westcott.clj-otel.context :as context]))


(defn- <client-request
  "Make an asynchronous HTTP request using `hato` and return a channel of the
   response."
  [client request]
  (style'/<respond-raise
   (fn [respond raise]
     (let [request (conj request
                         {:async?           true
                          :throw-exceptions false
                          :http-client      client})]

       ;; Manually create a client span. The client span is ended when either a
       ;; response or exception is returned.
       (span/async-bound-span (trace-http/client-span-opts request)
                              (fn [respond* raise*]

                                (let [;; Propagate context containing client span to remote
                                      ;; server by injecting headers. This enables span
                                      ;; correlation to make distributed traces.
                                      request' (update request :headers merge (context/->headers))]

                                  ;; Use `bound-fn` to ensure respond/raise fns use bound
                                  ;; context
                                  (client/request
                                   request'
                                   (bound-fn [response]

                                     ;; Add HTTP response data to the client span.
                                     (trace-http/add-client-span-response-data!
                                      response)

                                     (respond* response))
                                   (bound-fn [e]

                                     ;; Add error information to client span.
                                     (trace-http/add-client-span-response-data!
                                      {:io.opentelemetry.api.trace.span.attrs/error-type e})
                                     (raise* e)))))
                              respond
                              raise)))))



(defn <get-sum
  "Get the sum of the nums and return a channel of the result."
  [{:keys [client]} nums]
  (let [endpoint  (get-in config [:endpoints :sum-service])
        <response (<client-request client
                                   {:method       :get
                                    :url          (str endpoint "/sum")
                                    :query-params {:nums (str/join "," nums)}
                                    :accept       :json
                                    :as           :json})]
    (style/async
      (let [response (style/await <response)
            {:keys [status body]} response]
        (if (= 200 status)
          (:sum body)
          (throw (ex-info (str status " HTTP response")
                          {:http.response/status status
                           :service/error        :service.errors/unexpected-http-response})))))))
