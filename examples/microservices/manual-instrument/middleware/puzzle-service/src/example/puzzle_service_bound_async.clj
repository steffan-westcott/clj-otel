(ns example.puzzle-service-bound-async
  "Example application demonstrating using `clj-otel` to add telemetry to an
   asynchronous Ring HTTP service that is run without the OpenTelemetry
   instrumentation agent. In this example, the bound context default is used in
   `clj-otel` functions."
  (:require [clj-http.client :as client]
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
            [steffan-westcott.clj-otel.api.metrics.http.server :as metrics-http-server]
            [steffan-westcott.clj-otel.api.metrics.instrument :as instrument]
            [steffan-westcott.clj-otel.api.trace.http :as trace-http]
            [steffan-westcott.clj-otel.api.trace.span :as span]
            [steffan-westcott.clj-otel.context :as context]
            [steffan-westcott.clj-otel.instrumentation.runtime-telemetry-java17 :as
             runtime-telemetry])
  (:import (clojure.lang PersistentQueue)))

(defonce
  ^{:doc "Delay containing histogram that records the number of letters in each generated puzzle."}
  puzzle-size
  (delay (instrument/instrument {:name        "service.puzzle.puzzle-size"
                                 :instrument-type :histogram
                                 :unit        "{letters}"
                                 :description "The number of letters in each generated puzzle"})))



(def ^:private config
  {})



(defn client-request
  "Make an asynchronous HTTP request using `clj-http`."
  [request respond raise]

  ;; Manually create a client span. The client span is ended when either a
  ;; response or exception is returned.
  (span/async-bound-span
   (trace-http/client-span-opts request)
   (fn [respond* raise*]

     (let [;; Propagate context containing client span to remote
           ;; server by injecting headers. This enables span
           ;; correlation to make distributed traces.
           request' (update request :headers merge (context/->headers))]

       ;; `clj-http` restores bindings before evaluating callback function
       (client/request request'
                       (fn [response]

                         ;; Add HTTP response data to the client span.
                         (trace-http/add-client-span-response-data! response)

                         (respond* response))
                       raise*)))
   respond
   raise))



(defn <client-request
  "Make an asynchronous HTTP request and return a channel of the response."
  [request]
  (let [<ch    (async/chan)
        put-ch #(async/put! <ch %)]
    (client-request request put-ch put-ch)
    <ch))



(defn <get-random-word
  "Get a random word string of the requested type and return a channel of the
   word."
  [word-type]
  (let [endpoint  (get-in config [:endpoints :random-word-service] "http://localhost:8081")
        <response (<client-request {:method       :get
                                    :url          (str endpoint "/random-word")
                                    :query-params {"type" (name word-type)}
                                    :async        true
                                    :throw-exceptions false})]
    (async'/go-try
      (let [response (async'/<? <response)
            status   (:status response)]
        (if (= 200 status)
          (:body response)
          (throw (ex-info "Unexpected HTTP response"
                          {:type          ::ring/response
                           :response      {:status status
                                           :body   "Unexpected HTTP response"}
                           :service/error :service.errors/unexpected-http-response})))))))



(defn <random-words
  "Get random words of the requested types and return a channel containing
   a value for each word."
  [word-types]

  ;; Start a new internal span that ends when the source channel (returned by
  ;; the body) closes or 5000 milliseconds have elapsed. Returns a dest channel
  ;; with buffer size 2. Values are piped from source to dest irrespective of
  ;; timeout.
  (async'/<with-bound-span {:name       "Getting random words"
                            :attributes {:system/word-types word-types}}
                           5000
                           2

                           (async'/<concat (map <get-random-word word-types))))



(defn scramble
  "Scrambles a given word."
  [word]

  ;; Wrap synchronous function body with an internal span.
  (span/with-bound-span! {:name       "Scrambling word"
                          :attributes {:system/word word}}

    (Thread/sleep 5)
    (let [scrambled-word (->> word
                              seq
                              shuffle
                              (apply str))]

      ;; Add more attributes to internal span
      (span/add-span-data! {:attributes {:service.puzzle/scrambled-word scrambled-word}})

      scrambled-word)))



(defn <generate-puzzle
  "Constructs a puzzle string containing scrambled random words of the
   requested word types and returns a channel of the puzzle string."
  [word-types]
  (let [<words (<random-words word-types)]
    (async'/go-try
      (try
        (loop [scrambled-words (PersistentQueue/EMPTY)]
          (if-let [word (async'/<? <words)]
            (recur (conj scrambled-words (scramble word)))
            (do

              ;; Add event to span
              (span/add-span-data! {:event {:name       "Completed setting puzzle"
                                            :attributes {:system/puzzle scrambled-words}}})

              ;; Update puzzle-size metric
              (instrument/record! @puzzle-size {:value (reduce + (map count scrambled-words))})

              (str/join " " scrambled-words))))
        (finally
          (async'/close-and-drain!! <words))))))



(defn get-puzzle-handler
  "Asynchronous Ring handler for `GET /puzzle` request. Returns an HTTP
   response containing a puzzle of the requested word types."
  [{:keys [query-params]} respond raise]
  (let [word-types (map keyword (str/split (get query-params "types") #","))
        <puzzle    (<generate-puzzle word-types)]
    (async'/ch->respond-raise <puzzle
                              (fn [puzzle]
                                (respond (response/response puzzle)))
                              raise)))



(def handler
  "Ring handler for all requests."
  (ring/ring-handler (ring/router ["/puzzle"
                                   {:name ::puzzle
                                    :get  get-puzzle-handler}]
                                  {:data {:muuntaja   m/instance
                                          :middleware [;; Add route data
                                                       trace-http/wrap-reitit-route

                                                       ;; Add metrics that include http.route
                                                       ;; attribute
                                                       metrics-http-server/wrap-metrics-by-route

                                                       parameters/parameters-middleware
                                                       muuntaja/format-middleware
                                                       exception/exception-middleware

                                                       ;; Add exception event before
                                                       ;; exception-middleware runs
                                                       trace-http/wrap-exception-event]}})
                     (ring/create-default-handler)

                     ;; Wrap handling of all requests, including those which have no matching
                     ;; route. As this application is not run with the OpenTelemetry
                     ;; instrumentation agent, create a server span for each request.
                     {:middleware [[trace-http/wrap-server-span {:create-span? true}]
                                   [metrics-http-server/wrap-active-requests]]}))



(defn server
  "Starts puzzle-service server instance."
  ([conf]
   (server conf {}))
  ([conf jetty-opts]
   (alter-var-root #'config (constantly conf))

   ;; Register measurements that report metrics about the JVM runtime. These measurements cover
   ;; buffer pools, classes, CPU, garbage collector, memory pools and threads.
   (runtime-telemetry/register!)

   (jetty/run-jetty #'handler (assoc jetty-opts :async? true :port 8080))))



(comment
  (server {} {:join? false})
  ;
)
