(ns example.solar-system-service.async-cf-bound.requests
  "Requests to other microservices, async CompletableFuture implementation
   using bound context."
  (:require [example.solar-system-service.env :refer [config]]
            [hato.client :as client]
            [qbits.auspex :as aus]
            [steffan-westcott.clj-otel.context :as context]))


(defn- <client-request
  "Make an asynchronous HTTP request using `hato` and return a
   `CompletableFuture` of the response."
  [client request]
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
      (client/request request))))



(defn <get-statistic-value
  "Get a single statistic value of a planet and return a `CompletableFuture` of
   a single-valued map of the statistic and its value."
  [{:keys [client]} planet statistic]
  (let [endpoint  (get-in config [:endpoints :planet-service])
        path      (str "/planets/" (name planet) "/" (name statistic))
        <response (<client-request client
                                   {:method :get
                                    :url    (str endpoint path)
                                    :accept :json
                                    :as     :json})]
    (aus/then <response
              (fn [{:keys [status body]}]
                (if (= 200 status)
                  {statistic (:statistic body)}
                  (throw (ex-info (str status " HTTP response")
                                  {:http.response/status status
                                   :service/error :service.errors/unexpected-http-response})))))))
