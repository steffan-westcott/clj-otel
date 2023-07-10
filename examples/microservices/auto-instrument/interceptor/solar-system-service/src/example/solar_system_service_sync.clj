(ns example.solar-system-service-sync
  "Example application demonstrating using `clj-otel` to add telemetry to a
   synchronous Pedestal HTTP service that is run with the OpenTelemetry
   instrumentation agent."
  (:require [clj-http.client :as client]
            [clojure.string :as str]
            [example.common.interceptor.utils :as interceptor-utils]
            [io.pedestal.http :as http]
            [io.pedestal.http.route :as route]
            [io.pedestal.interceptor :as interceptor]
            [ring.util.response :as response]
            [steffan-westcott.clj-otel.api.metrics.instrument :as instrument]
            [steffan-westcott.clj-otel.api.trace.http :as trace-http]
            [steffan-westcott.clj-otel.api.trace.span :as span]))


(defonce ^{:doc "Delay containing counter that records the number of planet reports built."}
         report-count
  (delay (instrument/instrument {:name        "service.solar-system.planet-report-count"
                                 :instrument-type :counter
                                 :unit        "{reports}"
                                 :description "The number of reports built"})))



(def ^:private config
  {})



(defn get-statistic-value
  "Get a single statistic value of a planet."
  [planet statistic]
  (let [endpoint (get-in config [:endpoints :planet-service] "http://localhost:8081")
        path     (str "/planets/" (name planet) "/" (name statistic))

        ;; Apache HttpClient request is automatically wrapped in a client span
        ;; created by the OpenTelemetry instrumentation agent. The agent also
        ;; propagates the context containing the client span to the remote HTTP
        ;; server by injecting headers into the request.
        response (client/get (str endpoint path) {:throw-exceptions false})
        status   (:status response)]

    (if (= 200 status)
      {statistic (Double/parseDouble (:body response))}
      (throw (ex-info (str status " HTTP response")
                      {:http.response/status status
                       :service/error        :service.errors/unexpected-http-response})))))



(defn planet-statistics
  "Get all statistics of a planet and return single-valued map values of each
   statistic."
  [planet]

  ;; Wrap synchronous function body with an internal span.
  (span/with-span! {:name       "Getting planet statistics"
                    :attributes {:system/planet planet}}

    ;; Use `doall` to force lazy sequence to be realized within span
    (doall (map #(get-statistic-value planet %) [:diameter :gravity]))))



(defn format-report
  "Returns a report string of the given planet and statistic values."
  [planet statistic-values]

  ;; Wrap synchronous function body with an internal span.
  (span/with-span! {:name       "Formatting report"
                    :attributes {:system/planet planet
                                 :service.solar-system.report/statistic-values statistic-values}}

    (Thread/sleep 25)
    (let [planet' (str/capitalize (name planet))
          {:keys [diameter gravity]} statistic-values
          report
          (str "The planet " planet' " has diameter " diameter "km and gravity " gravity "m/s^2.")]

      ;; Add more attributes to internal span
      (span/add-span-data! {:attributes {:service.solar-system.report/length (count report)}})

      ;; Update report-count metric
      (instrument/add! @report-count {:value 1})

      report)))



(defn planet-report
  "Builds a report of planet statistics and returns report string."
  [planet]
  (let [all-statistics   (planet-statistics planet)
        statistic-values (into {} all-statistics)]
    (format-report planet statistic-values)))



(defn get-statistics-handler
  "Synchronous handler for 'GET /statistics' request. Returns an HTTP response
   containing a formatted report of the planet's statistic values."
  [{:keys [query-params]}]
  (let [planet (keyword (get query-params :planet))
        report (planet-report planet)]
    (response/response report)))



(def routes
  "Route maps for the service."
  (route/expand-routes [[["/"
                          ^:interceptors
                          [(interceptor-utils/exception-response-interceptor)
                           (trace-http/exception-event-interceptor)]
                          ["/statistics" {:get 'get-statistics-handler}]]]]))



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
                               ::http/port   8080}
                              jetty-opts)))))



(comment
  (server {} {::http/join? false})
  ;
)