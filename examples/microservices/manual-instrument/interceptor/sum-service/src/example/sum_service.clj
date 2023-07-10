(ns example.sum-service
  "Example application demonstrating using `clj-otel` to add telemetry to a
   synchronous Pedestal HTTP service that is run without the OpenTelemetry
   instrumentation agent."
  (:require [clojure.string :as str]
            [example.common.interceptor.utils :as interceptor-utils]
            [io.pedestal.http :as http]
            [io.pedestal.http.route :as route]
            [io.pedestal.interceptor :as interceptor]
            [ring.util.response :as response]
            [steffan-westcott.clj-otel.api.metrics.http.server :as metrics-http-server]
            [steffan-westcott.clj-otel.api.metrics.instrument :as instrument]
            [steffan-westcott.clj-otel.api.trace.http :as trace-http]
            [steffan-westcott.clj-otel.api.trace.span :as span]
            [steffan-westcott.clj-otel.instrumentation.runtime-telemetry-java17 :as
             runtime-telemetry])
  (:gen-class))


(defonce ^{:doc "Delay containing histogram the records the resulting sum value."} sum-result
  (delay (instrument/instrument {:name        "service.sum.sum-result"
                                 :instrument-type :histogram
                                 :description "The resulting sum value"})))


(defn sum
  "Return the sum of the nums."
  [nums]

  ;; Wrap synchronous function body with an internal span.
  (span/with-span! {:name       "Calculating sum"
                    :attributes {:system/nums nums}}

    (Thread/sleep 50)
    (let [result (reduce + 0 nums)]

      ;; Add an event to the internal span with some data attached.
      (span/add-span-data! {:event {:name       "Computed sum"
                                    :attributes {:system/sum result}}})

      ;; Simulate an intermittent runtime exception when sum is 13.
      ;; An uncaught exception leaving a span's scope is reported as an
      ;; exception event and the span status description is set to the
      ;; exception triage summary.
      (when (= 13 result)
        (throw (RuntimeException. "Unlucky 13")))

      ;; Update sum-result metric
      (instrument/record! @sum-result {:value result})

      result)))



(defn get-sum-handler
  "Synchronous handler for `GET /sum` request. Returns an HTTP response
   containing the sum of the `nums` query parameters."
  [request]
  (let [{:keys [query-params]} request
        num-str  (get query-params :nums)
        num-strs (->> (str/split num-str #",")
                      (map str/trim)
                      (filter seq))
        nums     (map #(Integer/parseInt %) num-strs)]

    ;; Simulate a client error when first number argument is zero. Exception data is added as
    ;; attributes to the exception event by default.
    (if (= 0 (first nums))
      (throw (ex-info "Zero argument"
                      {:http.response/status 400
                       :system/error         :service.sum.errors/zero-argument}))
      (response/response (str (sum nums))))))



(def routes
  "Route maps for the service."
  (route/expand-routes [[["/"
                          ^:interceptors
                          [(interceptor-utils/exception-response-interceptor)
                           (trace-http/exception-event-interceptor)]
                          ["/sum" {:get 'get-sum-handler}]]]]))




(defn update-default-interceptors
  "Returns `default-interceptors` with added interceptors for HTTP server
   span support."
  [default-interceptors]
  (map interceptor/interceptor
       (concat (;; As this application is not run with the OpenTelemetry instrumentation
                ;; agent, create a server span for each request. Because all request
                ;; processing for this service is synchronous, the current context is set
                ;; for each request.
                trace-http/server-span-interceptors {:create-span?         true
                                                     :set-current-context? true})

               ;; Add metric that records the number of active HTTP requests
               [(metrics-http-server/active-requests-interceptor)]

               ;; Default Pedestal interceptor stack
               default-interceptors

               ;; Adds matched route data to server spans
               [(trace-http/route-interceptor)]

               ;; Adds metrics that include http.route attribute
               (metrics-http-server/metrics-by-route-interceptors))))



(defn service
  "Returns an initialised service map ready for creating an HTTP server."
  [service-map]
  (-> service-map
      (http/default-interceptors)
      (update ::http/interceptors update-default-interceptors)
      (http/create-server)))



(defn server
  "Starts sum-service server instance."
  ([]
   (server {}))
  ([opts]

   ;; Register measurements that report metrics about the JVM runtime. These measurements cover
   ;; buffer pools, classes, CPU, garbage collector, memory pools and threads.
   (runtime-telemetry/register!)

   (http/start (service (conj {::http/routes routes
                               ::http/type   :jetty
                               ::http/host   "0.0.0.0"
                               ::http/port   8081}
                              opts)))))



(defn -main
  "sum-service application entry point."
  [& _args]
  (server))



(comment
  (server {::http/join? false})
  ;
)