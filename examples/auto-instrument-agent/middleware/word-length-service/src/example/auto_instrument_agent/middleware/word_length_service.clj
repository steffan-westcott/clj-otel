(ns example.auto-instrument-agent.middleware.word-length-service
  "Example application demonstrating using `clj-otel` to add telemetry to a
  synchronous Ring HTTP service that is run with the OpenTelemetry
  instrumentation agent."
  (:require [example.common-utils.middleware :as middleware]
            [muuntaja.core :as m]
            [reitit.ring :as ring]
            [reitit.ring.middleware.exception :as exception]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [reitit.ring.middleware.parameters :as parameters]
            [ring.adapter.jetty :as jetty]
            [ring.util.response :as response]
            [steffan-westcott.clj-otel.api.trace.http :as trace-http]
            [steffan-westcott.clj-otel.api.trace.span :as span]))

(defn word-length
  "Gets the length of the word."
  [word]

  ;; Manually create an internal span that wraps body (lexical scope)
  (span/with-span! {:name       "Calculating length"
                    :attributes {:system/word word}}

    (Thread/sleep (+ 50 (rand-int 80)))

    ;; Simulate an intermittent runtime exception.
    ;; An uncaught exception leaving a span's scope is reported as an
    ;; exception event and the span status description is set to the
    ;; exception triage summary.
    (when (= "boom" word)
      (throw (RuntimeException. "Unable to process word")))

    (let [word-length (count word)]

      ;; Add an event to the current span with some data attached
      (span/add-span-data! {:event {:name       "Calculated word length"
                                    :attributes {:system/word-length word-length}}})

      word-length)))



(defn get-length-handler
  "Synchronous Ring handler for 'GET /length' request. Returns an HTTP response
  containing the length of the word in the request."
  [{:keys [query-params]}]
  (let [word (get query-params "word")]

    ;; Simulate a client error for some requests.
    ;; Exception data is added as attributes to the exception event by default.
    (if (= word "problem")
      (throw (ex-info "Bad word argument"
                      {:type          ::ring/response
                       :response      {:status 400
                                       :body   "Bad word argument"}
                       :service/error :service.word-length.errors/bad-word
                       :system/word   word}))
      (response/response (str (word-length word))))))



(def handler
  "Ring handler for all requests."
  (ring/ring-handler (ring/router
                      ["/length"
                       {:name ::length
                        :get  get-length-handler}]
                      {:data {:muuntaja   m/instance
                              :middleware [;; Add route data
                                           middleware/wrap-reitit-route

                                           parameters/parameters-middleware
                                           muuntaja/format-middleware exception/exception-middleware

                                           ;; Add exception event before exception-middleware runs
                                           middleware/wrap-exception-event]}})
                     (ring/create-default-handler)

                     ;; Wrap handling of all requests, including those which have no matching route.
                     ;; As this application is run with the OpenTelemetry instrumentation agent, a
                     ;; server span will be provided by the agent and there is no need to create
                     ;; another one.
                     {:middleware [[trace-http/wrap-server-span {:create-span? false}]]}))



(defonce ^{:doc "word-length-service server instance"} server
         (jetty/run-jetty #'handler
                          {:port  8081
                           :join? false}))
