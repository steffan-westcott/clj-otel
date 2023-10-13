(ns example.planet-service
  "Example application demonstrating using `clj-otel` to add telemetry to a
   synchronous Pedestal HTTP service that is run with the OpenTelemetry
   instrumentation agent."
  (:require [aero.core :as aero]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [example.common.interceptor.utils :as interceptor-utils]
            [io.pedestal.http :as http]
            [io.pedestal.http.route :as route]
            [io.pedestal.interceptor :as interceptor]
            [ring.util.response :as response]
            [steffan-westcott.clj-otel.api.metrics.instrument :as instrument]
            [steffan-westcott.clj-otel.api.trace.http :as trace-http]
            [steffan-westcott.clj-otel.api.trace.span :as span])
  (:gen-class))


(defonce ^{:doc "Delay containing counter that records the number of statistic look ups."}
         statistic-lookup-count
  (delay (instrument/instrument {:name        "service.planet.statistic-lookup-count"
                                 :instrument-type :counter
                                 :unit        "{lookups}"
                                 :description "The number of statistic lookups"})))



(def planet-statistics
  "Map of planets and their statistics. Saturn is missing some data."
  {:mercury {:diameter 4879
             :gravity  3.7}
   :venus   {:diameter 12104
             :gravity  8.9}
   :earth   {:diameter 12756
             :gravity  9.8}
   :mars    {:diameter 6792
             :gravity  3.7}
   :jupiter {:diameter 142984
             :gravity  23.1}
   :saturn  {:diameter 120536
             :gravity  nil} ; missing gravity value
   :uranus  {:diameter 51118
             :gravity  8.7}
   :neptune {:diameter 49528
             :gravity  11.0}
   :pluto   {:diameter 2370
             :gravity  0.7}})



(defn planet-statistic
  "Returns a specific statistic value for a planet."
  [planet statistic]

  ;; Wrap the synchronous body in a new internal span.
  (span/with-span! ["Fetching statistic value"
                    {:system/planet    planet
                     :system/statistic statistic}]

    (Thread/sleep 50)
    (let [path [planet statistic]]

      ;; Add an event to the current span with some attributes attached.
      (span/add-event! "Processed query path" {:service.planet/query-path path})

      ;; Update statistic-lookup-count metric
      (instrument/add! @statistic-lookup-count
                       {:value      1
                        :attributes {:statistic statistic}})

      (if-let [result (get-in planet-statistics path)]

        result

        ;; Simulate an intermittent runtime exception when attempt is made to retrieve Saturn's
        ;; gravity value. An uncaught exception leaving a span's scope is reported as an
        ;; exception event and the span status description is set to the exception triage
        ;; summary.
        (throw (RuntimeException. "Failed to retrieve statistic"))))))



(defn get-planet-statistic-handler
  "Synchronous handler for 'GET /planets/:planet/:statistic' request. Returns
   an HTTP response containing the requested statistic value for a planet."
  [{:keys [path-params]}]
  (let [{:keys [planet statistic]} path-params
        planet    (keyword planet)
        statistic (keyword statistic)]

    ;; Simulate a client error when requesting data on Pluto. Exception data is added as
    ;; attributes to the exception event by default.
    (if (= planet :pluto)
      (throw (ex-info "Pluto is not a full planet"
                      {:http.response/status 400
                       :service/error        :service.planet.errors/pluto-not-full-planet}))
      (response/response (str (planet-statistic planet statistic))))))



(defn ping-handler
  "Handler for ping health check"
  [_]
  (response/response nil))



(def routes
  "Route maps for the service."
  (route/expand-routes
   [[["/"
      ^:interceptors
      [(interceptor-utils/exception-response-interceptor) (trace-http/exception-event-interceptor)]
      ["/ping" {:get 'ping-handler}]
      ["/planets/:planet/:statistic"
       ^:constraints
       {:planet    (re-pattern (str/join "|" (map name (keys planet-statistics))))
        :statistic #"diameter|gravity"} {:get 'get-planet-statistic-handler}]]]]))



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
  "Starts planet-service server instance."
  ([]
   (server {}))
  ([opts]
   (let [config (aero/read-config (io/resource "config.edn"))]
     (http/start (service (merge {::http/routes routes
                                  ::http/type   :jetty
                                  ::http/host   "0.0.0.0"}
                                 (:service-map config)
                                 opts))))))



(defn -main
  "planet-service application entry point."
  [& _args]
  (server))



(comment
  (server {::http/join? false})
  ;
)