(ns example.sentence-summary-service.async-cf-explicit.requests
  "Requests to other microservices, async CompletableFuture implementation
   using explicit context."
  (:require [example.sentence-summary-service.env :refer [config]]
            [hato.client :as client]
            [qbits.auspex :as aus]
            [reitit.ring :as ring]
            [steffan-westcott.clj-otel.context :as context]))


(defn- <client-request
  "Make an asynchronous HTTP request using `hato` and return a
   `CompletableFuture` of the response."
  [client context request]
  (let [request (conj request
                      {:async?           true
                       :throw-exceptions false
                       :http-client      client})]

    ;; Set the current context just while the client request is
    ;; created. This ensures the client span created by the agent will
    ;; have the correct parent context.
    (context/with-context! context

      ;; hato request is automatically wrapped in a client span
      ;; created by the OpenTelemetry instrumentation agent. The
      ;; agent also propagates the context containing the client span
      ;; to the remote HTTP server by injecting headers into the
      ;; request.
      (client/request request))))



(defn <get-word-length
  "Get the length of `word` and return a `CompletableFuture` of the length
   value."
  [{:keys [client]} context word]
  (let [endpoint (get-in config [:endpoints :word-length-service])]
    (-> (<client-request client
                         context
                         {:method       :get
                          :url          (str endpoint "/length")
                          :query-params {:word word}
                          :accept       :json
                          :as           :json})
        (aus/then (fn [{:keys [status body]}]
                    (if (= 200 status)
                      (:length body)
                      (throw (ex-info (str status " HTTP response")
                                      {:type ::ring/response
                                       :response {:status status
                                                  :body   {:error "Unexpected HTTP response"}}
                                       :service/error
                                       :service.errors/unexpected-http-response}))))))))
