(ns example.auto-instrument-agent.interceptor.solar-system-service-async
  "Example application demonstrating using `clj-otel` to add telemetry to an
  asynchronous Pedestal HTTP service that is run with the OpenTelemetry
  instrumentation agent."
  (:require [clj-http.client :as client]
            [clojure.core.async :as async]
            [clojure.string :as str]
            [example.common-utils.core-async :as async']
            [io.pedestal.http :as http]
            [io.pedestal.http.route :as route]
            [ring.util.response :as response]
            [steffan-westcott.otel.api.trace.http :as trace-http]
            [steffan-westcott.otel.api.trace.span :as span]
            [steffan-westcott.otel.context :as context]))


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
  (let [<ch (async/chan)
        put-ch #(async/put! <ch %)]
    (client-request context request put-ch put-ch)
    <ch))



(defn <get-metric-value
  "Get a single metric value of a planet and return a channel of a
  single-valued map of the metric and its value."
  [context planet metric]
  (let [path (str "/planets/" (name planet) "/" (name metric))
        <response (<client-request context
                                   {:method           :get
                                    :url              (str "http://localhost:8081" path)
                                    :async            true
                                    :throw-exceptions false})]
    (async'/go-try
      (let [response (async'/<? <response)]
        (if (= (:status response) 200)
          {metric (Double/parseDouble (:body response))}
          (throw (ex-info "planet-service failed"
                          {:server-status (:status response)})))))))



(defn <planet-metrics
  "Get all metrics of a planet and return a channel containing a single-valued
  map values of each metric."
  [context planet]

  ;; Start a new internal span that ends when the source channel (returned by
  ;; the body) closes or 4000 milliseconds have elapsed. Returns a dest channel
  ;; with buffer size 2. Values are piped from source to dest irrespective of
  ;; timeout. Context containing internal span is assigned to `context*`.
  (async'/<with-span-binding [context* {:parent     context
                                        :name       "Getting planet metrics"
                                        :attributes {:planet planet}}]
    4000 2

    (let [chs (map #(<get-metric-value context* planet %) [:diameter :gravity])]
      (async/merge chs))))



(defn format-report
  "Returns a report string of the given planet and metric values."
  [context planet metric-values]

  ;; Wrap synchronous function body with an internal span. Context containing
  ;; internal span is assigned to `context*`.
  (span/with-span-binding [context* {:parent     context
                                     :name       "Formatting report"
                                     :attributes {:planet planet
                                                  :values metric-values}}]

    (Thread/sleep 25)
    (let [planet' (str/capitalize (name planet))
          {:keys [diameter gravity]} metric-values
          report (str "The planet " planet' " has diameter " diameter "km and gravity " gravity "m/s^2.")]

      ;; Add more attributes to internal span
      (span/add-span-data! {:context    context*
                            :attributes (:report-length (count report))})

      report)))



(defn <planet-report
  "Builds a report of planet metrics and results a channel of the report
  string."
  [context planet]
  (let [<all-metrics (<planet-metrics context planet)
        <metric-values (async'/<into?? {} <all-metrics)]
    (async'/go-try
      (try
        (let [metric-values (async'/<? <metric-values)]
          (format-report context planet metric-values))
        (finally
          (async'/close-and-drain!! <all-metrics))))))


(defn <get-metrics
  "Asynchronous handler for 'GET /metrics' request. Returns a channel of the
  HTTP response containing a formatted report of the planet's metric values."
  [{:keys [io.opentelemetry/server-span-context request] :as ctx}]

  ;; Add data describing matched route to the server span.
  (trace-http/add-route-data! "/metrics" {:context server-span-context})

  (let [planet (keyword (get-in request [:query-params :planet]))
        <report (<planet-report server-span-context planet)]
    (async'/go-try-response ctx
                            (let [report (async'/<? <report)]
                              (response/response report)))))



(def get-metrics-interceptor
  {:name  ::get-metrics
   :enter <get-metrics})


(def routes
  "Route maps for the service."
  (route/expand-routes
    [[[
       ;; Wrap request handling of all routes. As this application is run with
       ;; the OpenTelemetry instrumentation agent, a server span will be
       ;; provided by the agent and there is no need to create another one.
       "/" (trace-http/server-span-interceptors {:create-span? false
                                                 :server-name  "solar"})

       ["/metrics" {:get 'get-metrics-interceptor}]]]]))



(def service-map
  {::http/routes routes
   ::http/type   :jetty
   ::http/port   8080
   ::http/join?  false})



(defn init-tracer!
  "Set default tracer used when manually creating spans."
  []
  (let [tracer (span/get-tracer {:name "solar-system-service" :version "1.0.0"})]
    (span/set-default-tracer! tracer)))


;;;;;;;;;;;;;

(init-tracer!)
(defonce server (http/start (http/create-server service-map)))

(comment

  )