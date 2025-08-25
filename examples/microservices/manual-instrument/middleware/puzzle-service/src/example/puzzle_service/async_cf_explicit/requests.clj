(ns example.puzzle-service.async-cf-explicit.requests
  "Requests to other microservices, async CompletableFuture implementation
   using explicit context."
  (:require [example.puzzle-service.env :refer [config]]
            [hato.client :as client]
            [qbits.auspex :as aus]
            [reitit.ring :as ring]
            [steffan-westcott.clj-otel.api.trace.http :as trace-http]
            [steffan-westcott.clj-otel.api.trace.span :as span]
            [steffan-westcott.clj-otel.context :as context]))


(defn- <client-request
  "Make an asynchronous HTTP request using `hato` and return a
   `CompletableFuture` of the response."
  [client context request]
  (let [request (conj request
                      {:async?           true
                       :throw-exceptions false
                       :http-client      client})]

    ;; Manually create a client span with `context` as the parent context.
    ;; Context containing client span is assigned to `context*`. Client span is
    ;; ended when either a response or exception is returned.
    (span/cf-span-binding [context* (trace-http/client-span-opts request {:parent context})]

      (-> (let [;; Propagate context containing client span to remote
                ;; server by injecting headers. This enables span
                ;; correlation to make distributed traces.
                request' (update request :headers merge (context/->headers {:context context*}))]

            (client/request request'))
          (aus/when-complete (fn [response e]

                               ;; Add HTTP response data to the client span.
                               (trace-http/add-client-span-response-data!
                                (if e
                                  {:io.opentelemetry.api.trace.span.attrs/error-type e}
                                  response)
                                {:context context*})))))))



(defn <get-random-word
  "Get a random word string of the requested type and return a
   `CompletableFuture` of the word."
  [{:keys [client]} context word-type]
  (let [endpoint (get-in config [:endpoints :random-word-service])]
    (-> (<client-request client
                         context
                         {:method       :get
                          :url          (str endpoint "/random-word")
                          :query-params {:type (name word-type)}
                          :accept       :json
                          :as           :json})
        (aus/then (fn [{:keys [status body]}]
                    (if (= 200 status)
                      (:word body)
                      (throw (ex-info (str status " HTTP response")
                                      {:type ::ring/response
                                       :response {:status status
                                                  :body   {:error "Unexpected HTTP response"}}
                                       :service/error
                                       :service.errors/unexpected-http-response}))))))))

