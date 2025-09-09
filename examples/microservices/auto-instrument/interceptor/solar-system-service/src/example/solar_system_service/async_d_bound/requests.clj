(ns example.solar-system-service.async-d-bound.requests
  "Requests to other microservices, Manifold implementation using bound
   context."
  (:require [example.common.async.manifold :as d']
            [example.solar-system-service.env :refer [config]]
            [hato.client :as client]
            [manifold.deferred :as d]
            [steffan-westcott.clj-otel.context :as context]))


(defn- <client-request
  "Make an asynchronous HTTP request using `hato` and return a deferred of the
   response."
  [client request]
  (d'/<respond-raise
   (fn [respond raise]
     (let [request (conj request
                         {:async?           true
                          :throw-exceptions false
                          :http-client      client})]

       ;; Set the current context to the bound context just while the
       ;; client request is created. This ensures the client span created
       ;; by the agent will have the correct parent context.
       (context/with-bound-context!

         ;; hato request is automatically wrapped in a client span
         ;; created by the OpenTelemetry instrumentation agent. The
         ;; agent also propagates the context containing the client span
         ;; to the remote HTTP server by injecting headers into the
         ;; request.
         (client/request request respond raise))))))



(defn <get-statistic-value
  "Get a single statistic value of a planet and return a deferred of a
   single-valued map of the statistic and its value."
  [{:keys [client]} planet statistic]
  (let [endpoint  (get-in config [:endpoints :planet-service])
        path      (str "/planets/" (name planet) "/" (name statistic))
        <response (<client-request client
                                   {:method :get
                                    :url    (str endpoint path)
                                    :accept :json
                                    :as     :json})]
    (d/chain' <response
              (fn [{:keys [status body]}]
                (if (= 200 status)
                  {statistic (:statistic body)}
                  (throw (ex-info (str status " HTTP response")
                                  {:http.response/status status
                                   :service/error :service.errors/unexpected-http-response})))))))
