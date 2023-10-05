(ns example.sentence-summary-service-bound-async
  "Example application demonstrating using `clj-otel` to add telemetry to an
   asynchronous Ring HTTP service that is run with the OpenTelemetry
   instrumentation agent. In this example, the bound context default is used in
   `clj-otel` functions."
  (:require [clj-http.client :as client]
            [clj-http.conn-mgr :as conn]
            [clj-http.core :as http-core]
            [clojure.core.async :as async]
            [clojure.string :as str]
            [example.common.core-async.utils :as async']
            [muuntaja.core :as m]
            [reitit.ring :as ring]
            [reitit.ring.middleware.exception :as exception]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [reitit.ring.middleware.parameters :as parameters]
            [ring.adapter.jetty :as jetty]
            [ring.util.response :as response]
            [steffan-westcott.clj-otel.api.metrics.instrument :as instrument]
            [steffan-westcott.clj-otel.api.trace.http :as trace-http]
            [steffan-westcott.clj-otel.api.trace.span :as span]
            [steffan-westcott.clj-otel.context :as context]))


(defonce ^{:doc "Delay containing histogram that records the number of words in each sentence."}
         words-count
  (delay (instrument/instrument {:name        "service.sentence-summary.words-count"
                                 :instrument-type :histogram
                                 :unit        "{words}"
                                 :description "The number of words in each sentence"})))



(def ^:private config
  {})

(def ^:private async-conn-mgr
  (delay (conn/make-reusable-async-conn-manager {})))

(def ^:private async-client
  (delay (http-core/build-async-http-client {} @async-conn-mgr)))



(defn client-request
  "Make an asynchronous HTTP request using `clj-http`."
  [request respond raise]

  (let [request (conj request
                      {:async true
                       :throw-exceptions false
                       :connection-manager @async-conn-mgr
                       :http-client @async-client})]

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
  [request]
  (let [<ch    (async/chan)
        put-ch #(async/put! <ch %)]
    (client-request request put-ch put-ch)
    <ch))



(defn <get-word-length
  "Get the length of `word` and return a channel of the length value."
  [word]
  (let [endpoint  (get-in config [:endpoints :word-length-service] "http://localhost:8081")
        <response (<client-request {:method       :get
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



(defn <word-lengths
  "Get the word lengths and return a channel containing a value for each word
  length."
  [words]

  ;; Start a new internal span that ends when the source channel (returned by
  ;; the body) closes or 6000 milliseconds have elapsed. Returns a dest channel
  ;; with buffer size 3. Values are piped from source to dest irrespective of
  ;; timeout.
  (async'/<with-bound-span ["Getting word lengths" {:system/words words}]
                           6000
                           3

                           (async/merge (map <get-word-length words))))



(defn summary
  "Returns a summary of the given word lengths."
  [lengths]

  ;; Wrap synchronous function body with an internal span.
  (span/with-bound-span! ["Building sentence summary" {:system/word-lengths lengths}]

    (Thread/sleep 25)
    (let [result {:word-count      (count lengths)
                  :shortest-length (apply min lengths)
                  :longest-length  (apply max lengths)}]

      ;; Add more attributes to internal span
      (span/add-span-data! {:attributes {:service.sentence-summary.summary/word-count (:word-count
                                                                                       result)}})

      ;; Update words-count metric
      (instrument/record! @words-count {:value (count lengths)})

      result)))



(defn <build-summary
  "Builds a summary of the words in the sentence and returns a channel of the
  summary value."
  [sentence]
  (let [words        (str/split sentence #"\s+")
        <all-lengths (<word-lengths words)
        <lengths     (async'/<into?? [] <all-lengths)]
    (async'/go-try
      (try
        (let [lengths (async'/<? <lengths)]
          (summary lengths))
        (finally
          (async'/close-and-drain!! <all-lengths))))))



(defn get-summary-handler
  "Asynchronous Ring handler for `GET /summary` request. Returns an HTTP
  response containing a summary of the words in the given sentence."
  [{:keys [query-params]} respond raise]
  (let [sentence (get query-params "sentence")
        <summary (<build-summary sentence)]
    (async'/ch->respond-raise <summary
                              (fn [summary]
                                (respond (response/response (str summary))))
                              raise)))



(defn ping-handler
  "Ring handler for ping health check."
  [_ respond _]
  (respond (response/response nil)))



(def handler
  "Ring handler for all requests."
  (ring/ring-handler (ring/router [["/ping"
                                    {:name ::ping
                                     :get  ping-handler}]
                                   ["/summary"
                                    {:name ::summary
                                     :get  get-summary-handler}]]
                                  {:data {:muuntaja   m/instance
                                          :middleware [;; Add route data
                                                       trace-http/wrap-reitit-route

                                                       parameters/parameters-middleware
                                                       muuntaja/format-middleware
                                                       exception/exception-middleware

                                                       ;; Add exception event before
                                                       ;; exception-middleware runs
                                                       trace-http/wrap-exception-event]}})
                     (ring/create-default-handler)

                     ;; Wrap handling of all requests, including those which have no matching
                     ;; route. As this application is run with the OpenTelemetry
                     ;; instrumentation agent, a server span will be provided by the agent and
                     ;; there is no need to create another one.
                     {:middleware [[trace-http/wrap-server-span {:create-span? false}]]}))



(defn server
  "Starts sentence-summary-service server instance."
  ([conf]
   (server conf {}))
  ([conf jetty-opts]
   (alter-var-root #'config (constantly conf))
   (jetty/run-jetty #'handler
                    (conj jetty-opts
                          {:async?      true
                           :max-threads 16
                           :port        8080}))))



(comment
  (server {} {:join? false})
  ;
)