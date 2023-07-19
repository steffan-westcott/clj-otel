(ns example.average-service-explicit-async
  "Example application demonstrating using `clj-otel` to add telemetry to an
   asynchronous Pedestal HTTP service that is run without the OpenTelemetry
   instrumentation agent. In this example, the context is explicitly passed in
   as a parameter to `clj-otel` functions."
  (:require [clj-http.client :as client]
            [clj-http.conn-mgr :as conn]
            [clj-http.core :as http-core]
            [clojure.core.async :as async]
            [clojure.string :as str]
            [example.common.core-async.utils :as async']
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

(def ^:private async-conn-mgr
  (delay (conn/make-reusable-async-conn-manager {})))

(def ^:private async-client
  (delay (http-core/build-async-http-client {} @async-conn-mgr)))



(defn client-request
  "Make an asynchronous HTTP request using `clj-http`."
  [context request respond raise]

  (let [request (conj request
                      {:async true
                       :throw-exceptions false
                       :connection-manager @async-conn-mgr
                       :http-client @async-client})]

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
     raise)))



(defn <client-request
  "Make an asynchronous HTTP request and return a channel of the response."
  [context request]
  (let [<ch    (async/chan)
        put-ch #(async/put! <ch %)]
    (client-request context request put-ch put-ch)
    <ch))



(defn <get-sum
  "Get the sum of the nums and return a channel of the result."
  [context nums]
  (let [endpoint  (get-in config [:endpoints :sum-service] "http://localhost:8081")
        <response (<client-request context
                                   {:method       :get
                                    :url          (str endpoint "/sum")
                                    :query-params {"nums" (str/join "," nums)}})]
    (async'/go-try
      (let [response (async'/<? <response)
            status   (:status response)]
        (if (= 200 status)
          (Integer/parseInt (:body response))
          (throw (ex-info (str status " HTTP response")
                          {:http.response/status status
                           :service/error        :service.errors/unexpected-http-response})))))))


(defn divide
  "Divides x by y."
  [context x y]

  ;; Wrap synchronous function body with an internal span. Context containing
  ;; internal span is assigned to `context*`.
  (span/with-span-binding [context* {:parent     context
                                     :name       "Calculating division"
                                     :attributes {:service.average.divide/parameters [x y]}}]

    (Thread/sleep 10)
    (let [result (double (/ x y))]

      ;; Add more attributes to internal span
      (span/add-span-data! {:context    context*
                            :attributes {:service.average.divide/result result}})

      result)))



(defn <average
  "Calculate the average of the nums and return a channel of the result."
  [context nums]

  ;; Start a new internal span that ends when the source channel (returned by
  ;; the body) closes or 3000 milliseconds have elapsed. Returns a dest channel
  ;; with buffer size 1. Values are piped from source to dest irrespective of
  ;; timeout. Context containing internal span is assigned to `context*`.
  (async'/<with-span-binding [context* {:parent     context
                                        :name       "Calculating average"
                                        :attributes {:system/nums nums}}]
    3000
    1

    (let [<sum (<get-sum context* nums)]
      (async'/go-try
        (divide context* (async'/<? <sum) (count nums))))))



(defn <averages
  "Calculates the averages of the odd numbers and the even numbers of nums and
   returns a channel of the result."
  [context nums]
  (let [odds          (filter odd? nums)
        evens         (filter even? nums)
        <odds-average (when (seq odds)
                        (<average context odds))
        <evens-average (when (seq evens)
                         (<average context evens))]
    (async'/go-try
      (let [odds-average  (when <odds-average
                            (async'/<? <odds-average))
            evens-average (when <evens-average
                            (async'/<? <evens-average))
            result        {:odds  odds-average
                           :evens evens-average}]

        ;; Add event to span
        (span/add-span-data! {:context context
                              :event   {:name       "Finished calculations"
                                        :attributes {:system.averages/odds  odds-average
                                                     :system.averages/evens evens-average}}})

        ;; Update average-result metric
        (doseq [[partition average] result]
          (when average
            (instrument/record! @average-result
                                {:context    context
                                 :value      average
                                 :attributes {:partition partition}})))

        result))))



(defn <get-averages
  "Asynchronous handler for 'GET /average' request. Returns a channel of the
   HTTP response containing calculated averages of the `nums` query parameters."
  [{:keys [io.opentelemetry/server-span-context request]
    :as   ctx}]

  (let [num-str  (get-in request [:query-params :nums])
        num-strs (->> (str/split num-str #",")
                      (map str/trim)
                      (filter seq))
        nums     (map #(Integer/parseInt %) num-strs)
        <avs     (<averages server-span-context nums)]
    (async'/go-try-response ctx
      (let [avs (async'/<? <avs)]
        (response/response (str avs))))))


(def get-averages-interceptor
  "Interceptor for 'GET /average' route."
  {:name  ::get-averages
   :enter <get-averages})



(def routes
  "Route maps for the service."
  (route/expand-routes [[["/"
                          ^:interceptors
                          [(interceptor-utils/exception-response-interceptor)
                           (trace-http/exception-event-interceptor)]
                          ["/average" {:get 'get-averages-interceptor}]]]]))



(defn update-default-interceptors
  "Returns `default-interceptors` with added interceptors for HTTP server
   span support."
  [default-interceptors]
  (map interceptor/interceptor
       (concat (;; As this application is not run with the OpenTelemetry instrumentation
                ;; agent, create a server span for each request. Because part of this
                ;; service uses asynchronous processing, the current context is not set on
                ;; each request.
                trace-http/server-span-interceptors {:create-span?         true
                                                     :set-current-context? false})

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