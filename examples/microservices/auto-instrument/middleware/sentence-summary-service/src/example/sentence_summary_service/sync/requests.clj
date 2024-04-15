(ns example.sentence-summary-service.sync.requests
  "Requests to other microservices, synchronous implementation."
  (:require [clj-http.client :as client]
            [reitit.ring :as ring]))


(defn get-word-length
  "Get the length of `word`."
  [{:keys [config conn-mgr client]} word]

  ;; Apache HttpClient request is automatically wrapped in a client span
  ;; created by the OpenTelemetry instrumentation agent. The agent also
  ;; propagates the context containing the client span to the remote HTTP
  ;; server by injecting headers into the request.
  (let [endpoint (get-in config [:endpoints :word-length-service])
        response (client/get (str endpoint "/length")
                             {:throw-exceptions false
                              :connection-manager conn-mgr
                              :http-client  client
                              :query-params {"word" word}
                              :accept       :json
                              :as           :json})
        {:keys [status body]} response]
    (if (= 200 status)
      (:length body)
      (throw (ex-info "Unexpected HTTP response"
                      {:type          ::ring/response
                       :response      {:status status
                                       :body   {:error "Unexpected HTTP response"}}
                       :service/error :service.errors/unexpected-http-response})))))
