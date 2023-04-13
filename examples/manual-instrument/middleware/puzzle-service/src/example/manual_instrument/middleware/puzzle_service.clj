(ns example.manual-instrument.middleware.puzzle-service
  "Example application demonstrating using `clj-otel` to add telemetry to a
   synchronous Ring HTTP service that is run without the OpenTelemetry
   instrumentation agent."
  (:require [clj-http.client :as client]
            [clojure.string :as str]
            [example.common-utils.middleware :as middleware]
            [muuntaja.core :as m]
            [reitit.ring :as ring]
            [reitit.ring.middleware.exception :as exception]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [reitit.ring.middleware.parameters :as parameters]
            [ring.adapter.jetty :as jetty]
            [ring.util.response :as response]
            [steffan-westcott.clj-otel.api.metrics.http.server :as metrics-http-server]
            [steffan-westcott.clj-otel.api.metrics.instrument :as instrument]
            [steffan-westcott.clj-otel.api.trace.http :as trace-http]
            [steffan-westcott.clj-otel.api.trace.span :as span]
            [steffan-westcott.clj-otel.context :as context]
            [steffan-westcott.clj-otel.instrumentation.runtime-metrics :as runtime-metrics]))


(defonce ^{:doc "Histogram that records the number of letters in each generated puzzle."}
         puzzle-size
  (instrument/instrument {:name        "service.puzzle.puzzle-size"
                          :instrument-type :histogram
                          :unit        "{letters}"
                          :description "The number of letters in each generated puzzle"}))



(defn client-request
  "Perform a synchronous HTTP request using `clj-http`."
  [request]

  ;; Wrap the synchronous body in a new client span.
  (span/with-span! (trace-http/client-span-opts request)

    (let [;; Propagate context containing client span to remote
          ;; server by injecting headers. This enables span
          ;; correlation to make distributed traces.
          request' (update request :headers merge (context/->headers))

          response (client/request request')]

      ;; Add HTTP response data to the client span.
      (trace-http/add-client-span-response-data! response)

      response)))



(defn get-random-word
  "Get a random word string of the requested type."
  [word-type]
  (let [response (client-request {:method       :get
                                  :url          "http://localhost:8081/random-word"
                                  :query-params {"type" (name word-type)}
                                  :throw-exceptions false})
        status   (:status response)]
    (if (= 200 status)
      (:body response)
      (throw (ex-info "Unexpected HTTP response"
                      {:type          ::ring/response
                       :response      {:status status
                                       :body   "Unexpected HTTP response"}
                       :service/error :service.errors/unexpected-http-response})))))



(defn random-words
  "Get random words of the requested types."
  [word-types]

  ;; Wrap the synchronous body in a new internal span.
  (span/with-span! {:name       "Getting random words"
                    :attributes {:system/word-types word-types}}

    ;; Use `doall` to force lazy sequence to be realized within span
    (doall (map get-random-word word-types))))



(defn scramble
  "Scrambles a given word."
  [word]
  (span/with-span! {:name       "Scrambling word"
                    :attributes {:system/word word}}

    (Thread/sleep 5)
    (let [scrambled-word (->> word
                              seq
                              shuffle
                              (apply str))]

      ;; Add more attributes to internal span
      (span/add-span-data! {:attributes {:service.puzzle/scrambled-word scrambled-word}})

      scrambled-word)))



(defn generate-puzzle
  "Constructs a puzzle string containing scrambled random words of the
   requested word types."
  [word-types]
  (let [words (random-words word-types)
        scrambled-words (map scramble words)]

    ;; Add event to span
    (span/add-span-data! {:event {:name       "Completed setting puzzle"
                                  :attributes {:system/puzzle scrambled-words}}})

    ;; Update puzzle-size metric
    (instrument/record! puzzle-size {:value (reduce + (map count scrambled-words))})

    (str/join " " scrambled-words)))



(defn get-puzzle-handler
  "Synchronous Ring handler for `GET /puzzle` request. Returns an HTTP
   response containing a puzzle of the requested word types."
  [{:keys [query-params]}]
  (let [word-types (map keyword (str/split (get query-params "types") #","))
        puzzle     (generate-puzzle word-types)]
    (response/response puzzle)))



(def handler
  "Ring handler for all requests."
  (ring/ring-handler (ring/router ["/puzzle"
                                   {:name ::puzzle
                                    :get  get-puzzle-handler}]
                                  {:data {:muuntaja   m/instance
                                          :middleware [;; Add route data
                                                       middleware/wrap-reitit-route

                                                       ;; Add metrics that include http.route
                                                       ;; attribute
                                                       metrics-http-server/wrap-metrics-by-route

                                                       parameters/parameters-middleware
                                                       muuntaja/format-middleware
                                                       exception/exception-middleware

                                                       ;; Add exception event
                                                       middleware/wrap-exception-event]}})
                     (ring/create-default-handler)

                     ;; Wrap handling of all requests, including those which have no matching route.
                     ;; As this application is not run with the OpenTelemetry instrumentation agent,
                     ;; create a server span for each request.
                     {:middleware [[trace-http/wrap-server-span {:create-span? true}]
                                   [metrics-http-server/wrap-active-requests]]}))


;; Register measurements that report metrics about the JVM runtime. These measurements cover
;; buffer pools, classes, CPU, garbage collector, memory pools and threads.
(defonce ^{:doc "JVM metrics registration"} _jvm-reg
  (runtime-metrics/register!))


(defonce ^{:doc "puzzle-service server instance"} server
  (jetty/run-jetty #'handler
                   {:port  8080
                    :join? false}))
