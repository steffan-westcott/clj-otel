(ns example.auto-instrument-agent.interceptor.solar-system-service-async
  "Example application demonstrating using `clj-otel` to add telemetry to an
  asynchronous Pedestal HTTP service that is run with the OpenTelemetry
  instrumentation agent."
  (:require [clj-http.client :as client]
            [clojure.core.async :as async]
            [clojure.string :as str]
            [example.common-utils.core-async :as async']
            [example.common-utils.interceptor :as interceptor]
            [io.pedestal.http :as http]
            [io.pedestal.http.route :as route]
            [ring.util.response :as response]
            [steffan-westcott.clj-otel.api.trace.http :as trace-http]
            [steffan-westcott.clj-otel.api.trace.span :as span]
            [steffan-westcott.clj-otel.context :as context]))


(defn client-request
  "Make an asynchronous HTTP request using `clj-http`."
  [context request respond raise]

  ;; Set the current context just while the client request is created. This
  ;; ensures the client span created by the agent will have the correct parent
  ;; context.
  (context/with-context! context

    ;; Apache HttpClient request is automatically wrapped in a client span
    ;; created by the OpenTelemetry instrumentation agent. The agent also
    ;; propagates the context containing the client span to the remote HTTP
    ;; server by injecting headers into the request.
    (client/request request respond raise)))



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
  (let [path      (str "/planets/" (name planet) "/" (name statistic))
        <response (<client-request context
                                   {:method :get
                                    :url    (str "http://localhost:8081" path)
                                    :async  true
                                    :throw-exceptions false})]
    (async'/go-try
      (let [response (async'/<? <response)
            status   (:status response)]
        (if (= 200 status)
          {statistic (Double/parseDouble (:body response))}
          (throw (ex-info (str status " HTTP response")
                          {:http.response/status status
                           :service/error        :service.errors/unexpected-http-response})))))))



(defn <planet-statistics
  "Get all statistics of a planet and return a channel containing a single-valued
  map values of each statistic."
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

  ;; Add data describing matched route to the server span.
  (trace-http/add-route-data! "/statistics" {:context server-span-context})

  (let [planet  (keyword (get-in request [:query-params :planet]))
        <report (<planet-report server-span-context planet)]
    (async'/go-try-response ctx
      (let [report (async'/<? <report)]
        (response/response report)))))



(def get-statistics-interceptor
  "Interceptor for 'GET /statistics' route."
  {:name  ::get-statistics
   :enter <get-statistics})



(def root-interceptors
  "Interceptors for all routes."
  (conj

   ;; As this application is run with the OpenTelemetry instrumentation agent,
   ;; a server span will be provided by the agent and there is no need to
   ;; create another one.
   (trace-http/server-span-interceptors {:create-span? false
                                         :server-name  "solar"})

   (interceptor/exception-response-interceptor)))



(def routes
  "Route maps for the service."
  (route/expand-routes [[["/" root-interceptors
                          ["/statistics" {:get 'get-statistics-interceptor}]]]]))



(def service-map
  "Pedestal service map for solar system HTTP service."
  {::http/routes routes
   ::http/type   :jetty
   ::http/port   8080
   ::http/join?  false})



(defonce ^{:doc "solar-system-service server instance"} server
         (http/start (http/create-server service-map)))
