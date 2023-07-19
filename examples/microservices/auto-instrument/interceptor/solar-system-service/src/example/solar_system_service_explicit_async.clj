(ns example.solar-system-service-explicit-async
  "Example application demonstrating using `clj-otel` to add telemetry to an
   asynchronous Pedestal HTTP service that is run with the OpenTelemetry
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
            [steffan-westcott.clj-otel.api.metrics.instrument :as instrument]
            [steffan-westcott.clj-otel.api.trace.http :as trace-http]
            [steffan-westcott.clj-otel.api.trace.span :as span]
            [steffan-westcott.clj-otel.context :as context]))


(defonce ^{:doc "Delay containing counter that records the number of planet reports built."}
         report-count
  (delay (instrument/instrument {:name        "service.solar-system.planet-report-count"
                                 :instrument-type :counter
                                 :unit        "{reports}"
                                 :description "The number of reports built"})))



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

    ;; Set the current context just while the client request is created. This
    ;; ensures the client span created by the agent will have the correct parent
    ;; context.
    (context/with-context! context

      ;; Apache HttpClient request is automatically wrapped in a client span
      ;; created by the OpenTelemetry instrumentation agent. The agent also
      ;; propagates the context containing the client span to the remote HTTP
      ;; server by injecting headers into the request.
      (client/request request respond raise))))



(defn <client-request
  "Make an asynchronous HTTP request and return a channel of the response."
  [context request]
  (let [<ch    (async/chan)
        put-ch #(async/put! <ch %)]
    (client-request context request put-ch put-ch)
    <ch))



(defn <get-statistic-value
  "Get a single statistic value of a planet and return a channel of a
   single-valued map of the statistic and its value."
  [context planet statistic]
  (let [endpoint  (get-in config [:endpoints :planet-service] "http://localhost:8081")
        path      (str "/planets/" (name planet) "/" (name statistic))
        <response (<client-request context
                                   {:method :get
                                    :url    (str endpoint path)})]
    (async'/go-try
      (let [response (async'/<? <response)
            status   (:status response)]
        (if (= 200 status)
          {statistic (Double/parseDouble (:body response))}
          (throw (ex-info (str status " HTTP response")
                          {:http.response/status status
                           :service/error        :service.errors/unexpected-http-response})))))))



(defn <planet-statistics
  "Get all statistics of a planet and return a channel containing a
   single-valued map values of each statistic."
  [context planet]

  ;; Start a new internal span that ends when the source channel (returned by
  ;; the body) closes or 4000 milliseconds have elapsed. Returns a dest channel
  ;; with buffer size 2. Values are piped from source to dest irrespective of
  ;; timeout. Context containing internal span is assigned to `context*`.
  (async'/<with-span-binding [context* {:parent     context
                                        :name       "Getting planet statistics"
                                        :attributes {:system/planet planet}}]
    4000
    2

    (let [chs (map #(<get-statistic-value context* planet %) [:diameter :gravity])]
      (async/merge chs))))



(defn format-report
  "Returns a report string of the given planet and statistic values."
  [context planet statistic-values]

  ;; Wrap synchronous function body with an internal span. Context containing
  ;; internal span is assigned to `context*`.
  (span/with-span-binding [context* {:parent     context
                                     :name       "Formatting report"
                                     :attributes {:system/planet planet
                                                  :service.solar-system.report/statistic-values
                                                  statistic-values}}]

    (Thread/sleep 25)
    (let [planet' (str/capitalize (name planet))
          {:keys [diameter gravity]} statistic-values
          report
          (str "The planet " planet' " has diameter " diameter "km and gravity " gravity "m/s^2.")]

      ;; Add more attributes to internal span
      (span/add-span-data! {:context    context*
                            :attributes {:service.solar-system.report/length (count report)}})

      ;; Update report-count metric
      (instrument/add! @report-count
                       {:context context*
                        :value   1})

      report)))



(defn <planet-report
  "Builds a report of planet statistics and results a channel of the report
   string."
  [context planet]
  (let [<all-statistics   (<planet-statistics context planet)
        <statistic-values (async'/<into?? {} <all-statistics)]
    (async'/go-try
      (try
        (let [statistics-values (async'/<? <statistic-values)]
          (format-report context planet statistics-values))
        (finally
          (async'/close-and-drain!! <all-statistics))))))



(defn <get-statistics
  "Asynchronous handler for 'GET /statistics' request. Returns a channel of the
   HTTP response containing a formatted report of the planet's statistic
   values."
  [{:keys [io.opentelemetry/server-span-context request]
    :as   ctx}]

  (let [planet  (keyword (get-in request [:query-params :planet]))
        <report (<planet-report server-span-context planet)]
    (async'/go-try-response ctx
      (let [report (async'/<? <report)]
        (response/response report)))))



(def get-statistics-interceptor
  "Interceptor for 'GET /statistics' route."
  {:name  ::get-statistics
   :enter <get-statistics})



(def routes
  "Route maps for the service."
  (route/expand-routes [[["/"
                          ^:interceptors
                          [(interceptor-utils/exception-response-interceptor)
                           (trace-http/exception-event-interceptor)]
                          ["/statistics" {:get 'get-statistics-interceptor}]]]]))



(defn update-default-interceptors
  "Returns `default-interceptors` with added interceptors for HTTP server span
   support."
  [default-interceptors]
  (map interceptor/interceptor
       (concat (;; As this application is run with the OpenTelemetry instrumentation agent,
                ;; a server span will be provided by the agent and there is no need to
                ;; create another one.
                trace-http/server-span-interceptors {:create-span? false})

               ;; Default Pedestal interceptor stack
               default-interceptors

               ;; Adds matched route data to server spans
               [(trace-http/route-interceptor)])))



(defn service
  "Returns an initialised service map ready for creating an HTTP server."
  [service-map]
  (-> service-map
      (http/default-interceptors)
      (update ::http/interceptors update-default-interceptors)
      (http/create-server)))



(defn server
  "Starts solar-system-service server instance."
  ([conf]
   (server conf {}))
  ([conf jetty-opts]
   (alter-var-root #'config (constantly conf))
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