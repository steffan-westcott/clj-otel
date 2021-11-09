(ns example.auto-instrument-agent.interceptor.solar-system-service
  "Example application demonstrating using `clj-otel` to add telemetry to a
  synchronous Pedestal HTTP service that is run with the OpenTelemetry
  instrumentation agent."
  (:require [clj-http.client :as client]
            [clojure.string :as str]
            [io.pedestal.http :as http]
            [io.pedestal.http.route :as route]
            [ring.util.response :as response]
            [steffan-westcott.otel.api.trace.http :as trace-http]
            [steffan-westcott.otel.api.trace.span :as span]))


(defn get-metric-value
  "Get a single metric value of a planet."
  [planet metric]
  (let [path (str "/planets/" (name planet) "/" (name metric))

        ;; Apache HttpClient request is automatically wrapped in a client span
        ;; created by the OpenTelemetry instrumentation agent. The agent also
        ;; propagates the context containing the client span to the remote HTTP
        ;; server by injecting headers into the request.
        response (client/get (str "http://localhost:8081" path)
                             {:throw-exceptions false})]

    (if (= (:status response) 200)
      {metric (Double/parseDouble (:body response))}
      (throw (ex-info "planet-service failed"
                      {:server-status (:status response)})))))



(defn planet-metrics
  "Get all metrics of a planet and return single-valued map values of each
  metric."
  [planet]

  ;; Wrap synchronous function body with an internal span.
  (span/with-span! {:name       "Getting planet metrics"
                    :attributes {:planet planet}}

    ;; Use `doall` to force lazy sequence to be realized within span
    (doall (map #(get-metric-value planet %) [:diameter :gravity]))))



(defn format-report
  "Returns a report string of the given planet and metric values."
  [planet metric-values]

  ;; Wrap synchronous function body with an internal span.
  (span/with-span! {:name       "Formatting report"
                    :attributes {:planet planet
                                 :values metric-values}}

    (Thread/sleep 25)
    (let [planet' (str/capitalize (name planet))
          {:keys [diameter gravity]} metric-values
          report (str "The planet " planet' " has diameter " diameter "km and gravity " gravity "m/s^2.")]

      ;; Add more attributes to internal span
      (span/add-span-data! {:attributes (:report-length (count report))})

      report)))



(defn planet-report
  "Builds a report of planet metrics and returns report string."
  [planet]
  (let [all-metrics (planet-metrics planet)
        metric-values (into {} all-metrics)]
    (format-report planet metric-values)))



(defn get-metrics-handler
  "Synchronous handler for 'GET /metrics' request. Returns an HTTP response
  containing a formatted report of the planet's metric values."
  [{:keys [query-params]}]

  ; Add data describing matched route to the server span.
  (trace-http/add-route-data! "/metrics")

  (let [planet (keyword (get query-params :planet))
        report (planet-report planet)]
    (response/response report)))


(def routes
  "Route maps for the service."
  (route/expand-routes
    [[[
       ;; Wrap request handling of all routes. As this application is run with
       ;; the OpenTelemetry instrumentation agent, a server span will be
       ;; provided by the agent and there is no need to create another one.
       "/" (trace-http/server-span-interceptors {:create-span? false
                                                 :server-name  "solar"})

       ["/metrics" {:get 'get-metrics-handler}]]]]))



(def service-map
  "Pedestal service map for solar system HTTP service."
  {::http/routes routes
   ::http/type   :jetty
   ::http/port   8080
   ::http/join?  false})



(defonce ^{:doc "solar-system-service server instance"}
         server (http/start (http/create-server service-map)))