(ns example.solar-system-service.async-task-explicit.requests
  "Requests to other microservices, Missionary implementation using explicit
   context."
  (:require [example.solar-system-service.env :refer [config]]
            [hato.client :as client]
            [missionary.core :as m]
            [steffan-westcott.clj-otel.context :as context])
  (:import (java.util.concurrent CancellationException CompletableFuture)))


(defn- <client-request
  "Make an asynchronous HTTP request using `hato` and return a task of the
   response."
  [client context request]
  (fn [respond raise]
    (let [request (conj request
                        {:async?           true
                         :throw-exceptions false
                         :http-client      client})

          ;; Set the current context just while the client request is
          ;; created. This ensures the client span created by the agent will
          ;; have the correct parent context.
          ^CompletableFuture cf (context/with-context! context

                                  ;; hato request is automatically wrapped in a client span
                                  ;; created by the OpenTelemetry instrumentation agent. The
                                  ;; agent also propagates the context containing the client
                                  ;; span to the remote HTTP server by injecting headers into
                                  ;; the request.
                                  (client/request request respond raise))]
      #(do
         (future-cancel cf)
         (when (future-cancelled? cf)
           (raise (CancellationException.)))))))



(defn <get-statistic-value
  "Get a single statistic value of a planet and return a task of a
   single-valued map of the statistic and its value."
  [{:keys [client]} context planet statistic]
  (let [endpoint  (get-in config [:endpoints :planet-service])
        path      (str "/planets/" (name planet) "/" (name statistic))
        <response (<client-request client
                                   context
                                   {:method :get
                                    :url    (str endpoint path)
                                    :accept :json
                                    :as     :json})]
    (m/sp
      (let [{:keys [status body]} (m/? <response)]
        (if (= 200 status)
          {statistic (:statistic body)}
          (throw (ex-info (str status " HTTP response")
                          {:http.response/status status
                           :service/error        :service.errors/unexpected-http-response})))))))
