(ns example.auto-instrument-agent.middleware.word-length-service
  "Example application demonstrating using `clj-otel` to add telemetry to a
  synchronous Ring HTTP service that is run with the OpenTelemetry
  instrumentation agent."
  (:require [example.common-utils.middleware :as middleware]
            [ring.adapter.jetty :as jetty]
            [ring.middleware.params :as params]
            [ring.util.response :as response]
            [steffan-westcott.clj-otel.api.trace.http :as trace-http]
            [steffan-westcott.clj-otel.api.trace.span :as span]))

(defn word-length
  "Gets the length of the word."
  [word]

  ;; Manually create an internal span that wraps body (lexical scope)
  (span/with-span! {:name       "Calculating length"
                    :attributes {:my-arg word}}

    (Thread/sleep (+ 50 (rand-int 80)))

    ;; Simulate an intermittent runtime exception.
    ;; An uncaught exception leaving a span's scope is reported as an
    ;; exception event and the span status description is set to the
    ;; exception triage summary.
    (when (= "boom" word)
      (throw (ex-info "Unable to process word"
                      {:status        500
                       :error         ::word-processing-error
                       ::problem-word word})))

    (let [result (count word)]

      ;; Add an event to the current span with some data attached
      (span/add-span-data! {:event {:name       "Calculated length"
                                    :attributes {:my-result result}}})

      result)))


(defn get-length-handler
  "Synchronous Ring handler for 'GET /length' request. Returns an HTTP response
  containing the length of the word in the request."
  [{:keys [query-params]}]

  ; Add attributes describing matched route to server span
  (trace-http/add-route-data! "/length")

  (let [word (get query-params "word")]

    ;; Simulate a client error for some requests.
    (if (= word "problem")
      (throw (ex-info "Bad word argument"
                      {:status 400
                       :error  ::bad-word-argument}))
      (response/response (str (word-length word))))))


(defn handler
  "Synchronous Ring handler for all requests."
  [{:keys [request-method uri]
    :as   request}]
  (case [request-method uri]
    [:get "/length"] (get-length-handler request)
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
                                    :server-name  "word-length"})))



(defonce ^{:doc "word-length-service server instance"} server
         (jetty/run-jetty #'service
                          {:port  8081
                           :join? false}))
