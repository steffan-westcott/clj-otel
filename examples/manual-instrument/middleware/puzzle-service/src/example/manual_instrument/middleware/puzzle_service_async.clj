(ns example.manual-instrument.middleware.puzzle-service-async
  "Example application demonstrating using `clj-otel` to add telemetry to an
  asynchronous Ring HTTP service that is run without the OpenTelemetry
  instrumentation agent."
  (:require [clj-http.client :as client]
            [clojure.core.async :as async]
            [clojure.string :as str]
            [example.common-utils.core-async :as async']
            [example.common-utils.middleware :as middleware]
            [ring.adapter.jetty :as jetty]
            [ring.middleware.params :as params]
            [ring.util.response :as response]
            [steffan-westcott.clj-otel.api.trace.http :as trace-http]
            [steffan-westcott.clj-otel.api.trace.span :as span]
            [steffan-westcott.clj-otel.context :as context]
            [steffan-westcott.clj-otel.instrumentation.runtime-metrics :as runtime-metrics])
  (:import (clojure.lang PersistentQueue)))


(defn client-request
  "Make an asynchronous HTTP request using `clj-http`."
  [context request respond raise]

  ;; Manually create a client span with `context` as the parent context.
  ;; Context containing client span is assigned to `context*`. Client span is
  ;; ended when either a response or exception is returned.
  (span/async-span
   (trace-http/client-span-opts request {:parent context})
   (fn [context* respond* raise*]

     (let [;; Propagate context containing client span to remote
           ;; server by injecting headers. This enables span
           ;; correlation to make distributed traces.
           request' (update request :headers merge (context/->headers {:context context*}))]

       (client/request request'
                       (fn [response]

                         ;; Add HTTP response data to the client span.
                         (trace-http/add-client-span-response-data! response {:context context*})

                         (respond* response))
                       raise*)))
   respond
   raise))



(defn <client-request
  "Make an asynchronous HTTP request and return a channel of the response."
  [context request]
  (let [<ch    (async/chan)
        put-ch #(async/put! <ch %)]
    (client-request context request put-ch put-ch)
    <ch))



(defn <get-random-word
  "Get a random word string of the requested type and return a channel of the
  word."
  [context word-type]
  (let [<response (<client-request context
                                   {:method       :get
                                    :url          "http://localhost:8081/random-word"
                                    :query-params {"type" (name word-type)}
                                    :async        true
                                    :throw-exceptions false})]
    (async'/go-try
      (let [response (async'/<? <response)
            status   (:status response)]
        (if (= 200 status)
          (:body response)
          (throw (ex-info (str status " HTTP response")
                          {:http.response/status status
                           :service/error        :service.errors/unexpected-http-response})))))))



(defn <random-words
  "Get random words of the requested types and return a channel containing
  a value for each word."
  [context word-types]

  ;; Start a new internal span that ends when the source channel (returned by
  ;; the body) closes or 5000 milliseconds have elapsed. Returns a dest channel
  ;; with buffer size 2. Values are piped from source to dest irrespective of
  ;; timeout. Context containing internal span is assigned to `context*`.
  (async'/<with-span-binding [context* {:parent     context
                                        :name       "Getting random words"
                                        :attributes {:system/word-types word-types}}]
    5000
    2

    (let [<words* (map #(<get-random-word context* %) word-types)]
      (async'/<concat <words*))))



(defn scramble
  "Scrambles a given word."
  [context word]

  ;; Wrap synchronous function body with an internal span. Context containing
  ;; internal span is assigned to `context*`.
  (span/with-span-binding [context* {:parent     context
                                     :name       "Scrambling word"
                                     :attributes {:system/word word}}]

    (Thread/sleep 5)
    (let [scrambled-word (->> word
                              seq
                              shuffle
                              (apply str))]

      ;; Add more attributes to internal span
      (span/add-span-data! {:context    context*
                            :attributes {:service.puzzle/scrambled-word scrambled-word}})

      scrambled-word)))



(defn <generate-puzzle
  "Constructs a puzzle string containing scrambled random words of the
  requested word types and returns a channel of the puzzle string."
  [context word-types]
  (let [<words (<random-words context word-types)]
    (async'/go-try
      (try
        (loop [scrambled-words (PersistentQueue/EMPTY)]
          (if-let [word (async'/<? <words)]
            (recur (conj scrambled-words (scramble context word)))
            (do

              ;; Add event to span
              (span/add-span-data! {:context context
                                    :event   {:name       "Completed setting puzzle"
                                              :attributes {:system/puzzle scrambled-words}}})

              (str/join " " scrambled-words))))
        (finally
          (async'/close-and-drain!! <words))))))



(defn get-puzzle-handler
  "Asynchronous Ring handler for `GET /puzzle` request. Returns an HTTP
  response containing a puzzle of the requested word types."
  [{:keys [query-params io.opentelemetry/server-span-context]} respond raise]

  ;; Add data describing matched route to server span.
  (trace-http/add-route-data! "/puzzle" {:context server-span-context})

  (let [word-types (map keyword (str/split (get query-params "types") #","))
        <puzzle    (<generate-puzzle server-span-context word-types)]
    (async'/ch->respond-raise <puzzle
                              (fn [puzzle]
                                (respond (response/response puzzle)))
                              raise)))



(defn handler
  "Asynchronous Ring handler for all requests."
  [{:keys [request-method uri]
    :as   request} respond raise]
  (case [request-method uri]
    [:get "/puzzle"] (get-puzzle-handler request respond raise)
    (response/not-found "Not found")))



(def service
  "Ring handler with middleware applied."
  (-> handler
      params/wrap-params
      middleware/wrap-exception

      ;; Wrap request handling of all routes. As this application is not run
      ;; with the OpenTelemetry instrumentation agent, create a server span
      ;; for each request.
      (trace-http/wrap-server-span {:create-span? true})))


;; Register measurements that report metrics about the JVM runtime. These measurements cover
;; buffer pools, classes, CPU, garbage collector, memory pools and threads.
(runtime-metrics/register!)


(defonce ^{:doc "puzzle-service server instance"} server
         (jetty/run-jetty #'service
                          {:port   8080
                           :async? true
                           :join?  false}))
