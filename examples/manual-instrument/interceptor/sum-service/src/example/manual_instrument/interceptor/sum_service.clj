(ns example.manual-instrument.interceptor.sum-service
  "Example application demonstrating using `clj-otel` to add telemetry to a
  synchronous Pedestal HTTP service that is run without the OpenTelemetry
  instrumentation agent."
  (:require [clojure.string :as str]
            [example.common-utils.interceptor :as interceptor]
            [io.pedestal.http :as http]
            [io.pedestal.http.route :as route]
            [ring.util.response :as response]
            [steffan-westcott.otel.api.trace.http :as trace-http]
            [steffan-westcott.otel.api.trace.span :as span]))


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
        (throw (RuntimeException. "Unlucky 13")))

      result)))



(defn get-sum-handler
  "Synchronous handler for `GET /sum` request. Returns an HTTP response
  containing the sum of the `nums` query parameters."
  [request]
  (let [{:keys [query-params]} request]

    ; Add data describing matched route to server span.
    (trace-http/add-route-data! "/sum")

    (let [num-str (get query-params :nums)
          num-strs (->> (str/split num-str #",") (map str/trim) (filter seq))
          nums (map #(Integer/parseInt %) num-strs)]

      ;; Simulate a client error when first number argument is zero.
      (if (= 0 (first nums))
        (throw (ex-info "Zero argument" {:status 400 :error ::zero-argument}))
        (response/response (str (sum nums)))))))



(def root-interceptors
  "Interceptors for all routes."
  (conj

    ;; As this application is not run with the OpenTelemetry instrumentation
    ;; agent, create a server span for each request. Because all request
    ;; processing for this service is synchronous, the current context is set
    ;; for each request.
    (trace-http/server-span-interceptors {:create-span?         true
                                          :set-current-context? true
                                          :server-name          "sum"})

    (interceptor/exception-response-interceptor)))



(def routes
  "Route maps for the service."
  (route/expand-routes
    [[["/" root-interceptors
       ["/sum" {:get 'get-sum-handler}]]]]))



(def service-map
  "Pedestal service map for sum HTTP service."
  {::http/routes routes
   ::http/type   :jetty
   ::http/port   8081
   ::http/join?  false})



(defonce ^{:doc "sum-service server instance"} server
         (http/start (http/create-server service-map)))
