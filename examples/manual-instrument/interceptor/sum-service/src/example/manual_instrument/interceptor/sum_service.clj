(ns example.manual-instrument.interceptor.sum-service
  "Example application demonstrating using `clj-otel` to add telemetry to a
  synchronous Pedestal HTTP service that is run without the OpenTelemetry
  instrumentation agent."
  (:require [steffan-westcott.otel.api.trace.span :as span]
            [steffan-westcott.otel.api.trace.http :as trace-http]
            [io.pedestal.http :as http]
            [io.pedestal.http.route :as route]
            [clojure.string :as str]))


(defn sum
  "Return the sum of the nums."
  [nums]

  ;; Wrap synchronous function body with an internal span.
  (span/with-span! {:name       "Calculating sum"
                    :attributes {:the-nums nums}}

    (Thread/sleep 50)
    (let [result (reduce + 0 nums)]

      ;; Add an event to the internal span with some data attached.
      (span/add-span-data! {:event {:name       "Computed sum"
                                    :attributes {:answer result}}})

      ;; Simulate an intermittent runtime exception when sum is 13.
      ;; An uncaught exception leaving a span's scope is reported as an
      ;; exception event and the span status description is set to the
      ;; exception triage summary.
      (when (= 13 result)
        (throw (ex-info "Unlucky 13" {:error :superstition})))

      result)))



(defn get-sum-handler
  "Synchronous handler for `GET /sum` request. Returns an HTTP response
  containing the sum of the `nums` query parameters."
  [request]
  (let [{:keys [query-params]} request]

    ; Add data describing matched route to server span.
    (trace-http/add-route-data! "/sum")

    (let [num-str (get query-params :nums)
          nums (map #(Integer/parseInt %) (str/split num-str #","))]

      ;; Simulate a client error when first number argument is zero.
      ;; The server span status description may be set for 4xx and 5xx responses.
      (if (zero? (first nums))
        {:status 400 :io.opentelemetry.api.trace.span.status/description "First number is zero"}
        {:status 200 :body (str (sum nums))}))))


(def routes
  "Route maps for the service."
  (route/expand-routes
    [[[
       ;; Wrap request handling of all routes. As this application is not run
       ;; with the OpenTelemetry instrumentation agent, create a server span
       ;; for each request. Because all request processing for this service
       ;; is synchronous, the current context is set for each request.
       "/" (trace-http/server-span-interceptors {:create-span?         true
                                                 :set-current-context? true
                                                 :server-name          "sum"})

       ["/sum" {:get 'get-sum-handler}]]]]))


(def service-map
  {::http/routes routes
   ::http/type   :jetty
   ::http/port   8081
   ::http/join?  false})


(defn init-tracer!
  "Set default tracer used when manually creating spans."
  []
  (let [tracer (span/get-tracer {:name "sum-service" :version "1.0.0"})]
    (span/set-default-tracer! tracer)))


;;;;;;;;;;;;;

(init-tracer!)
(defonce server (http/start (http/create-server service-map)))

(comment
  )