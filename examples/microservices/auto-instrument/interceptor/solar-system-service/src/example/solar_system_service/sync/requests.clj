(ns example.solar-system-service.sync.requests
  "Requests to other microservices, synchronous implementation."
  (:require [clj-http.client :as client]))


(defn get-statistic-value
  "Get a single statistic value of a planet."
  [{:keys [config conn-mgr client]} planet statistic]
  (let [endpoint (get-in config [:endpoints :planet-service])
        path     (str "/planets/" (name planet) "/" (name statistic))

        ;; Apache HttpClient request is automatically wrapped in a client span
        ;; created by the OpenTelemetry instrumentation agent. The agent also
        ;; propagates the context containing the client span to the remote HTTP
        ;; server by injecting headers into the request.
        response (client/get (str endpoint path)
                             {:throw-exceptions   false
                              :connection-manager conn-mgr
                              :http-client        client})
        status   (:status response)]

    (if (= 200 status)
      {statistic (Double/parseDouble (:body response))}
      (throw (ex-info (str status " HTTP response")
                      {:http.response/status status
                       :service/error        :service.errors/unexpected-http-response})))))
