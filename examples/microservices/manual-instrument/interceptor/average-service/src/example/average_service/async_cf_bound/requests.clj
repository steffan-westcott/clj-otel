(ns example.average-service.async-cf-bound.requests
  "Requests to other microservices, async CompletableFuture implementation
   using bound context."
  (:require [clojure.string :as str]
            [example.average-service.env :refer [config]]
            [hato.client :as client]
            [qbits.auspex :as aus]
            [steffan-westcott.clj-otel.api.trace.http :as trace-http]
            [steffan-westcott.clj-otel.api.trace.span :as span]
            [steffan-westcott.clj-otel.context :as context]))


(defn- <client-request
  "Make an asynchronous HTTP request using `hato` and return a
   `CompletableFuture` of the response."
  [client request]
  (let [request (conj request
                      {:async?           true
                       :throw-exceptions false
                       :http-client      client})]

    ;; Manually create a client span. The client span is ended when either a
    ;; response or exception is returned.
    (span/async-bound-cf-span
     (trace-http/client-span-opts request)

     (-> (let [;; Propagate context containing client span to remote
               ;; server by injecting headers. This enables span
               ;; correlation to make distributed traces.
               request' (update request :headers merge (context/->headers))]

           (client/request request'))
         (aus/when-complete (bound-fn [response e]

                              ;; Add HTTP response data to the client span.
                              (trace-http/add-client-span-response-data!
                               (if e
                                 {:io.opentelemetry.api.trace.span.attrs/error-type e}
                                 response))))))))



(defn <get-sum
  "Get the sum of the nums and return a `CompletableFuture` of the result."
  [{:keys [client]} nums]
  (let [endpoint (get-in config [:endpoints :sum-service])]
    (-> (<client-request client
                         {:method       :get
                          :url          (str endpoint "/sum")
                          :query-params {:nums (str/join "," nums)}
                          :accept       :json
                          :as           :json})
        (aus/then (bound-fn [{:keys [status body]}]
                    (if (= 200 status)
                      (:sum body)
                      (throw (ex-info (str status " HTTP response")
                                      {:http.response/status status
                                       :service/error
                                       :service.errors/unexpected-http-response}))))))))
