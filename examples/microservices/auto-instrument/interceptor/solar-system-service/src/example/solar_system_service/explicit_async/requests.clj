(ns example.solar-system-service.explicit-async.requests
  "Requests to other microservices, explicit async implementation."
  (:require [clj-http.client :as client]
            [clojure.core.async :as async]
            [example.common.core-async.utils :as async']
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



(defn <get-statistic-value
  "Get a single statistic value of a planet and return a channel of a
   single-valued map of the statistic and its value."
  [{:keys [config]
    :as   components} context planet statistic]
  (let [endpoint  (get-in config [:endpoints :planet-service])
        path      (str "/planets/" (name planet) "/" (name statistic))
        <response (<client-request components
                                   context
                                   {:method :get
                                    :url    (str endpoint path)})]
    (async'/go-try
      (let [response (async'/<? <response)
            status   (:status response)]
        (if (= 200 status)
          {statistic (Double/parseDouble (:body response))}
          (throw (ex-info (str status " HTTP response")
                          {:http.response/status status
                           :service/error        :service.errors/unexpected-http-response})))))))
