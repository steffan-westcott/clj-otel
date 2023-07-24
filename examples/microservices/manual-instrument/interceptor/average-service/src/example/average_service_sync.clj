(ns example.average-service-sync
  "Example application demonstrating using `clj-otel` to add telemetry to a
   synchronous Pedestal HTTP service that is run without the OpenTelemetry
   instrumentation agent."
  (:require [clj-http.client :as client]
            [clj-http.conn-mgr :as conn]
            [clj-http.core :as http-core]
            [clojure.string :as str]
            [example.common.interceptor.utils :as interceptor-utils]
            [io.pedestal.http :as http]
            [io.pedestal.http.route :as route]
            [io.pedestal.interceptor :as interceptor]
            [ring.util.response :as response]
            [steffan-westcott.clj-otel.api.metrics.http.server :as metrics-http-server]
            [steffan-westcott.clj-otel.api.metrics.instrument :as instrument]
            [steffan-westcott.clj-otel.api.trace.http :as trace-http]
            [steffan-westcott.clj-otel.api.trace.span :as span]
            [steffan-westcott.clj-otel.context :as context]
            [steffan-westcott.clj-otel.instrumentation.runtime-telemetry-java17 :as
             runtime-telemetry]))


(defonce ^{:doc "Delay containing histogram that records the resulting averages."} average-result
  (delay (instrument/instrument {:name        "service.average.average-result"
                                 :instrument-type :histogram
                                 :description "The resulting averages"})))



(def ^:private config
  {})

(def ^:private conn-mgr
  (delay (conn/make-reusable-conn-manager {})))

(def ^:private sync-client
  (delay (http-core/build-http-client {} false @conn-mgr)))



(defn client-request
  "Perform a synchronous HTTP request using `clj-http`."
  [request]

  (let [request (conj request
                      {:throw-exceptions   false
                       :connection-manager @conn-mgr
                       :http-client        @sync-client})]

    ;; Wrap the synchronous body in a new client span.
    (span/with-span! (trace-http/client-span-opts request)

      (let [;; Propagate context containing client span to remote
            ;; server by injecting headers. This enables span
            ;; correlation to make distributed traces.
            request' (update request :headers merge (context/->headers))

            response (client/request request')]

        ;; Add HTTP response data to the client span.
        (trace-http/add-client-span-response-data! response)

        response))))



(defn get-sum
  "Get the sum of the nums."
  [nums]
  (let [endpoint (get-in config [:endpoints :sum-service] "http://localhost:8081")
        response (client-request {:method       :get
                                  :url          (str endpoint "/sum")
                                  :query-params {"nums" (str/join "," nums)}})
        status   (:status response)]
    (if (= 200 status)
      (Integer/parseInt (:body response))
      (throw (ex-info (str status " HTTP response")
                      {:http.response/status status
                       :service/error        :service.errors/unexpected-http-response})))))



(defn divide
  "Divides x by y."
  [x y]

  ;; Wrap synchronous function body with an internal span.
  (span/with-span! {:name       "Calculating division"
                    :attributes {:service.average.divide/parameters [x y]}}

    (Thread/sleep 10)
    (let [result (double (/ x y))]

      ;; Add more attributes to internal span
      (span/add-span-data! {:attributes {:service.average.divide/result result}})

      result)))



(defn average
  "Calculate the average of the nums."
  [nums]
  (let [sum (get-sum nums)]
    (divide sum (count nums))))



(defn averages
  "Calculates the averages of the odd numbers and the even numbers of nums and
   returns a channel of the result."
  [nums]
  (let [odds          (filter odd? nums)
        evens         (filter even? nums)
        odds-average  (when (seq odds)
                        (average odds))
        evens-average (when (seq evens)
                        (average evens))
        result        {:odds  odds-average
                       :evens evens-average}]

    ;; Add event to span
    (span/add-span-data! {:event {:name       "Finished calculations"
                                  :attributes {:system.averages/odds  odds-average
                                               :system.averages/evens evens-average}}})

    ;; Update average-result metric
    (doseq [[partition average] result]
      (when average
        (instrument/record! @average-result
                            {:value      average
                             :attributes {:partition partition}})))

    result))



(defn get-averages-handler
  "Synchronous handler for 'GET /average' request. Returns an HTTP response
   containing calculated averages of the odd and even numbers."
  [request]
  (let [{:keys [query-params]} request
        num-str  (get query-params :nums)
        num-strs (->> (str/split num-str #",")
                      (map str/trim)
                      (filter seq))
        nums     (map #(Integer/parseInt %) num-strs)
        avs      (averages nums)]
    (response/response (str avs))))



(defn ping-handler
  "Handler for ping health check"
  [_]
  (response/response nil))



(def routes
  "Route maps for the service."
  (route/expand-routes [[["/"
                          ^:interceptors
                          [(interceptor-utils/exception-response-interceptor)
                           (trace-http/exception-event-interceptor)] ["/ping" {:get 'ping-handler}]
                          ["/average" {:get 'get-averages-handler}]]]]))




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
  "Starts average-service server instance."
  ([conf]
   (server conf {}))
  ([conf jetty-opts]
   (alter-var-root #'config (constantly conf))

   ;; Register measurements that report metrics about the JVM runtime. These measurements cover
   ;; buffer pools, classes, CPU, garbage collector, memory pools and threads.
   (runtime-telemetry/register!)

   (http/start (service (conj {::http/routes routes
                               ::http/type   :jetty
                               ::http/host   "0.0.0.0"
                               ::http/port   8080
                               ::http/container-options {:max-threads 16}}
                              jetty-opts)))))



(comment
  (server {} {::http/join? false})
  ;
)