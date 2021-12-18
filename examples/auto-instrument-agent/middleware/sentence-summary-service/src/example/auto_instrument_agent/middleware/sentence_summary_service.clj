(ns example.auto-instrument-agent.middleware.sentence-summary-service
  "Example application demonstrating using `clj-otel` to add telemetry to a
  synchronous Ring HTTP service that is run with the OpenTelemetry
  instrumentation agent."
  (:require [clj-http.client :as client]
            [clojure.string :as str]
            [example.common-utils.middleware :as middleware]
            [ring.adapter.jetty :as jetty]
            [ring.middleware.params :as params]
            [ring.util.response :as response]
            [steffan-westcott.otel.api.trace.http :as trace-http]
            [steffan-westcott.otel.api.trace.span :as span]))

(defn get-word-length
  "Get the length of `word`."
  [word]

  ;; Apache HttpClient request is automatically wrapped in a client span
  ;; created by the OpenTelemetry instrumentation agent. The agent also
  ;; propagates the context containing the client span to the remote HTTP
  ;; server by injecting headers into the request.
  (let [response (client/get "http://localhost:8081/length"
                             {:throw-exceptions false
                              :query-params     {"word" word}})
        status (:status response)]

    (if (= 200 status)
      (Integer/parseInt (:body response))
      (throw (ex-info (str status " HTTP response")
                      {:status status
                       :error  :unexpected-http-response})))))



(defn word-lengths
  "Get the word lengths."
  [words]

  ;; Wrap synchronous function body with an internal span.
  (span/with-span! {:name       "Getting word lengths"
                    :attributes {:words words}}

    ;; Use `doall` to force lazy sequence to be realized within span
    (doall (map get-word-length words))))



(defn summary
  "Returns a summary of the given word lengths."
  [lengths]

  ;; Wrap synchronous function body with an internal span.
  (span/with-span! {:name       "Building sentence summary"
                    :attributes {:input-data lengths}}

    (Thread/sleep 25)
    (let [result {:word-count      (count lengths)
                  :shortest-length (apply min lengths)
                  :longest-length  (apply max lengths)}]

      ;; Add more attributes to internal span
      (span/add-span-data! {:attributes (select-keys result [:word-count])})

      result)))



(defn build-summary
  "Builds a summary of the words in the sentence."
  [sentence]
  (let [words (str/split sentence #"\s+")
        lengths (word-lengths words)]
    (summary lengths)))



(defn get-summary-handler
  "Synchronous Ring handler for `GET /summary` request. Returns an HTTP
  response containing a summary of the words in the given sentence."
  [{:keys [query-params]}]

  ;; Add attributes describing matched route to server span.
  (trace-http/add-route-data! "/summary")

  (let [sentence (get query-params "sentence")
        summary (build-summary sentence)]
    (response/response (str summary))))



(defn handler
  "Synchronous Ring handler for all requests."
  [{:keys [request-method uri] :as request}]
  (case [request-method uri]
    [:get "/summary"] (get-summary-handler request)
    (response/not-found "Not found")))


(def service
  "Ring handler with middleware applied."
  (-> handler
      params/wrap-params
      middleware/wrap-exception

      ;; Wrap request handling of all routes. As this application is run with
      ;; the OpenTelemetry instrumentation agent, a server span will be
      ;; provided by the agent and there is no need to create another one.
      (trace-http/wrap-server-span {:create-span? false
                                    :server-name  "sentence-summary"})))



(defonce ^{:doc "sentence-summary-service server instance"} server
         (jetty/run-jetty #'service {:port 8080 :join? false}))
