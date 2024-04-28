(ns example.solar-system-service.bound-async.requests
  "Requests to other microservices, bound async implementation."
  (:require [clj-http.client :as client]
            [clojure.core.async :as async]
            [example.common.core-async.utils :as async']
            [example.solar-system-service.env :refer [config]]
            [steffan-westcott.clj-otel.context :as context]))


(defn client-request
  "Make an asynchronous HTTP request using `clj-http`."
  [{:keys [conn-mgr client]} request respond raise]

  (let [request (conj request
                      {:async true
                       :throw-exceptions false
                       :connection-manager conn-mgr
                       :http-client client})]

    ;; Set the current context to the bound context just while the client request
    ;; is created. This ensures the client span created by the agent will have the
    ;; correct parent context.
    (context/with-bound-context!

      ;; Apache HttpClient request is automatically wrapped in a client span
      ;; created by the OpenTelemetry instrumentation agent. The agent also
      ;; propagates the context containing the client span to the remote HTTP
      ;; server by injecting headers into the request.
      (client/request request respond raise))))



(defn <client-request
  "Make an asynchronous HTTP request and return a channel of the response."
  [components request]
  (let [<ch    (async/chan)
        put-ch #(async/put! <ch %)]
    (client-request components request put-ch put-ch)
    <ch))



(defn <get-statistic-value
  "Get a single statistic value of a planet and return a channel of a
   single-valued map of the statistic and its value."
  [components planet statistic]
  (let [endpoint  (get-in config [:endpoints :planet-service])
        path      (str "/planets/" (name planet) "/" (name statistic))
        <response (<client-request components
                                   {:method :get
                                    :url    (str endpoint path)
                                    :accept :json
                                    :as     :json})]
    (async'/go-try
      (let [response (async'/<? <response)
            {:keys [status body]} response]
        (if (= 200 status)
          {statistic (:statistic body)}
          (throw (ex-info (str status " HTTP response")
                          {:http.response/status status
                           :service/error        :service.errors/unexpected-http-response})))))))
