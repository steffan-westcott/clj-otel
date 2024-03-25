(ns example.sentence-summary-service.explicit-async.requests
  "Requests to other microservices, explicit async implementation."
  (:require [clj-http.client :as client]
            [clojure.core.async :as async]
            [example.common.core-async.utils :as async']
            [reitit.ring :as ring]
            [steffan-westcott.clj-otel.context :as context]))


(defn client-request
  "Make an asynchronous HTTP request using `clj-http`."
  [{:keys [conn-mgr client]} context request respond raise]

  (let [request (conj request
                      {:async true
                       :throw-exceptions false
                       :connection-manager conn-mgr
                       :http-client client})]

    ;; Set the current context just while the client request is created. This
    ;; ensures the client span created by the agent will have the correct parent
    ;; context.
    (context/with-context! context

      ;; Apache HttpClient request is automatically wrapped in a client span
      ;; created by the OpenTelemetry instrumentation agent. The agent also
      ;; propagates the context containing the client span to the remote HTTP
      ;; server by injecting headers into the request.
      (client/request request respond raise))))



(defn <client-request
  "Make an asynchronous HTTP request and return a channel of the response."
  [components context request]
  (let [<ch    (async/chan)
        put-ch #(async/put! <ch %)]
    (client-request components context request put-ch put-ch)
    <ch))



(defn <get-word-length
  "Get the length of `word` and return a channel of the length value."
  [{:keys [config]
    :as   components} context word]
  (let [endpoint  (get-in config [:endpoints :word-length-service])
        <response (<client-request components
                                   context
                                   {:method       :get
                                    :url          (str endpoint "/length")
                                    :query-params {"word" word}})]
    (async'/go-try
      (let [response (async'/<? <response)
            status   (:status response)]
        (if (= 200 status)
          (Integer/parseInt (:body response))
          (throw (ex-info "Unexpected HTTP response"
                          {:type          ::ring/response
                           :response      {:status status
                                           :body   "Unexpected HTTP response"}
                           :service/error :service.errors/unexpected-http-response})))))))
