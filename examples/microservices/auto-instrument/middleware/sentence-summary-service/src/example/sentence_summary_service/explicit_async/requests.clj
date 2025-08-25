(ns example.sentence-summary-service.explicit-async.requests
  "Requests to other microservices, explicit async implementation."
  (:require [com.xadecimal.async-style :as style]
            [example.common.async.async-style :as style']
            [example.sentence-summary-service.env :refer [config]]
            [hato.client :as client]
            [reitit.ring :as ring]
            [steffan-westcott.clj-otel.context :as context]))


(defn- <client-request
  "Make an asynchronous HTTP request using `hato` and return a channel of the
   response."
  [client context request]
  (style'/<respond-raise
   (fn [respond raise]
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
         (client/request request respond raise))))))



(defn <get-word-length
  "Get the length of `word` and return a channel of the length value."
  [{:keys [client]} context word]
  (let [endpoint  (get-in config [:endpoints :word-length-service])
        <response (<client-request client
                                   context
                                   {:method       :get
                                    :url          (str endpoint "/length")
                                    :query-params {:word word}
                                    :accept       :json
                                    :as           :json})]
    (style/async
      (let [{:keys [status body]} (style/await <response)]
        (if (= 200 status)
          (:length body)
          (throw (ex-info "Unexpected HTTP response"
                          {:type          ::ring/response
                           :response      {:status status
                                           :body   {:error "Unexpected HTTP response"}}
                           :service/error :service.errors/unexpected-http-response})))))))
