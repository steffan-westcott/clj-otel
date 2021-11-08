(ns example.auto-instrument-agent.middleware.word-length-service
  "Example application demonstrating using `clj-otel` to add telemetry to a
  synchronous Ring HTTP service that is run with the OpenTelemetry
  instrumentation agent."
  (:require [steffan-westcott.otel.api.trace.span :as span]
            [steffan-westcott.otel.api.trace.http :as trace-http]
            [ring.adapter.jetty :as jetty]
            [ring.middleware.params :as params]
            [ring.util.response :as response]))

(defn word-length [word]

  ;; Manually create an internal span that wraps body (lexical scope)
  (span/with-span! {:name "Calculating length" :attributes {:my-arg word}}

    (Thread/sleep (+ 50 (rand-int 80)))

    ;; Simulate an intermittent runtime exception.
    ;; An uncaught exception leaving a span's scope is reported as an
    ;; exception event and the span status description is set to the
    ;; exception triage summary.
    (when (= "boom" word)
      (throw (ex-info "Unable to process word" {:problem-word word})))

    (let [result (count word)]

      ;; Add an event to the current span with some data attached
      (span/add-span-data! {:event {:name       "Calculated length"
                                    :attributes {:my-result result}}})

      result)))


(defn get-length-handler [{:keys [query-params]}]

  ; Add attributes describing matched route to server span
  (trace-http/add-route-data! "/length")

  (let [word (get query-params "word")]

    ;; Simulate a client error for some requests.
    (if (= word "problem")
      (response/bad-request "Cannot handle word")
      (response/response (str (word-length word))))))


(defn handler [{:keys [request-method uri] :as request}]
  (case [request-method uri]
    [:get "/length"] (get-length-handler request)
    (response/not-found "Not found")))


(def service
  "Ring handler with middleware applied."
  (-> handler
      params/wrap-params

      ;; Wrap request handling of all routes. As this application is run with
      ;; the OpenTelemetry instrumentation agent, a server span will be
      ;; provided by the agent and there is no need to create another one.
      (trace-http/wrap-server-span {:create-span? false
                                    :server-name  "word-length"})))


(defn init-tracer
  "Set default tracer used when manually creating spans."
  []
  (let [tracer (span/get-tracer {:name "word-length-service" :version "1.0.0"})]
    (span/set-default-tracer! tracer)))


;;;;;;;;;;;;;

(init-tracer)
(defonce server (jetty/run-jetty #'service {:port 8081 :join? false}))
