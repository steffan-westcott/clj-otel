(ns example.manual-instrument.middleware.random-word-service
  "Example application demonstrating using `clj-otel` to add telemetry to a
   synchronous Ring HTTP service that is run without the OpenTelemetry
   instrumentation agent."
  (:require [example.common-utils.middleware :as middleware]
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
            [steffan-westcott.clj-otel.instrumentation.runtime-telemetry-java8 :as
             runtime-telemetry]))


(def words
  "Map of word types and collections of random words of that type."
  {:noun      ["amusement" "bat" "cellar" "engine" "flesh" "frogs" "hearing" "record"]
   :verb      ["afford" "behave" "ignite" "justify" "race" "sprout" "strain" "wake"]
   :adjective ["cultured" "glorious" "grumpy" "handy" "kind" "lush" "mixed" "shut"]})



(defonce ^{:doc "Counter that records the number of words requested."} word-count
  (instrument/instrument {:name        "service.random-word.word-count"
                          :instrument-type :counter
                          :unit        "{words}"
                          :description "The number of words requested"}))


(defn random-word
  "Gets a random word of the requested type."
  [word-type]

  ;; Wrap the synchronous body in a new internal span.
  (span/with-span! {:name       "Generating word"
                    :attributes {:system/word-type word-type}}

    (Thread/sleep (+ 10 (rand-int 80)))

    ;; Simulate an intermittent runtime exception.
    ;; An uncaught exception leaving a span's scope is reported as an
    ;; exception event and the span status description is set to the
    ;; exception triage summary.
    (when (= :fault word-type)
      (throw (RuntimeException. "Processing fault")))

    (let [candidates (or (get words word-type)

                         ;; Exception data is added as attributes to the
                         ;; exception event by default.
                         (throw (ex-info
                                 "Unknown word type"
                                 {:type          ::ring/response
                                  :response      {:status 400
                                                  :body   "Unknown word type"}
                                  :service/error :service.random-word.errors/unknown-word-type
                                  :system/word-type word-type})))

          word       (rand-nth candidates)]

      ;; Add more attributes to the internal span
      (span/add-span-data! {:attributes {:system/word word}})

      ;; Update word-count metric
      (instrument/add! word-count
                       {:value      1
                        :attributes {:word-type word-type}})

      word)))



(defn get-random-word-handler
  "Synchronous Ring handler for 'GET /random-word' request. Returns an HTTP
   response containing a random word of the requested type."
  [{:keys [query-params]}]
  (let [type   (keyword (get query-params "type"))
        result (random-word type)]
    (Thread/sleep (+ 20 (rand-int 20)))
    (response/response (str result))))



(def handler
  "Ring handler for all requests."
  (ring/ring-handler (ring/router ["/random-word"
                                   {:name ::random-word
                                    :get  get-random-word-handler}]
                                  {:data {:muuntaja   m/instance
                                          :middleware [;; Add route data
                                                       middleware/wrap-reitit-route

                                                       ;; Add metrics that include http.route
                                                       ;; attribute
                                                       metrics-http-server/wrap-metrics-by-route

                                                       parameters/parameters-middleware
                                                       muuntaja/format-middleware
                                                       exception/exception-middleware

                                                       ;; Add exception event before
                                                       ;; exception-middleware runs
                                                       middleware/wrap-exception-event]}})
                     (ring/create-default-handler)

                     ;; Wrap handling of all requests, including those which have no matching
                     ;; route. As this application is not run with the OpenTelemetry
                     ;; instrumentation agent, create a server span for each request.
                     {:middleware [[trace-http/wrap-server-span {:create-span? true}]
                                   [metrics-http-server/wrap-active-requests]]}))


;; Register measurements that report metrics about the JVM runtime. These measurements cover
;; buffer pools, classes, CPU, garbage collector, memory pools and threads.
(defonce ^{:doc "JVM metrics registration"} _jvm-reg
  (runtime-telemetry/register!))


(defonce ^{:doc "random-word-service server instance"} server
  (jetty/run-jetty #'handler
                   {:port  8081
                    :join? false}))
