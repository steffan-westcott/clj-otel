(ns example.countries-service
  "Example application demonstrating using `clj-otel` to add telemetry to a
   synchronous Ring HTTP service that uses Compojure and run with the
   OpenTelemetry instrumentation agent."
  (:require [aero.core :as aero]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [compojure.core :refer [defroutes context GET] :as compojure]
            [compojure.route :as route]
            [org.corfield.logging4j2 :as log]
            [ring.adapter.jetty :as jetty]
            [ring.util.response :as response]
            [steffan-westcott.clj-otel.api.metrics.instrument :as instrument]
            [steffan-westcott.clj-otel.api.trace.http :as trace-http]
            [steffan-westcott.clj-otel.api.trace.span :as span]))



(defonce ^{:doc "Delay containing counter that records the number statistics lookups."}
         stat-lookup-count
  (delay (instrument/instrument {:name        "service.countries.stat-lookup-count"
                                 :instrument-type :counter
                                 :unit        "{lookups}"
                                 :description "The number of statistic lookups"})))



(def country-statistics
  "Map of some European countries and approximate statistics."
  {:deu {:name       "Germany"
         :area       360000
         :population 84000000}
   :esp {:name       "Spain"
         :area       500000
         :population 48000000}
   :fra {:name       "France"
         :area       640000
         :population 68000000}
   :gbr {:name       "United Kingdom"
         :area       240000
         :population 67000000}
   :ita {:name       "Italy"
         :area       nil ;; Missing value
         :population 59000000}})



(defn wrap-exception
  "Ring middleware for wrapping an exception as an HTTP response."
  [handler]
  (fn [request]
    (try
      (handler request)
      (catch Throwable e
        (let [info (ex-data e)]
          (if-let [resp (:http/response info)]
            resp
            (-> (response/response (ex-message e))
                (response/status 500))))))))



(defn country-statistic
  "Returns a specific statistic for a country."
  [country-code statistic]
  (let [iso-code (keyword (str/lower-case country-code))]

    ;; Simulate an intermittent runtime exception.
    (when (= :irl iso-code)
      (throw (RuntimeException. "Unable to process country statistic")))

    (log/debug {:message   "Looking up country statistic"
                :iso-code  iso-code
                :statistic statistic})
    (when-let [stat (get-in country-statistics [iso-code statistic])]

      (log/debug (str "Found stat " stat))

      ;; Increment lookup count for statistic
      (instrument/add! @stat-lookup-count
                       {:value      1
                        :attributes {:statistic statistic}})

      stat)))



(defn or-throw
  "Returns `stat` or throws exception."
  [stat]
  (or stat
      (throw (ex-info "Stat not found"
                      {:http/response (-> (response/response "Statistic not found")
                                          (response/status 404))}))))



(defroutes handler
  "Handler for all routes, including 404 route."
  (GET "/ping" [] "")
  (context "/country/:country-code"
           [country-code]
           (GET "/name" [] (or-throw (country-statistic country-code :name)))
           (GET "/area" [] (str (or-throw (country-statistic country-code :area))))
           (GET "/population" [] (str (or-throw (country-statistic country-code :population)))))
  (route/not-found "Not found"))



(def service
  "Ring handler with middleware applied."
  (-> handler

      ;; Add matched Compojure route to server span data, then put span around route
      ;; processing. `route/not-found` route is not considered as a match.
      (compojure/wrap-routes (comp trace-http/wrap-compojure-route span/wrap-span))

      ;; Convert exception to HTTP response
      wrap-exception

      ;; Add HTTP server span support to all requests, including those which have no matching
      ;; route
      trace-http/wrap-server-span))



(defn server
  "Starts countries-service server instance."
  ([]
   (server {}))
  ([jetty-opts]
   (let [config (aero/read-config (io/resource "config.edn"))]
     (jetty/run-jetty #'service (into (:jetty-opts config) jetty-opts)))))



(comment

  ;; Start the server
  (server {:join? false})

  ;; Use the following commands in a terminal to exercise the service

  ;; curl -X GET --location "http://localhost:8080/country/GBR/name"
  ;; curl -X GET --location "http://localhost:8080/country/ESP/population"
  ;; curl -X GET --location "http://localhost:8080/nothing-here"
  ;; curl -X GET --location "http://localhost:8080/country/ITA/area"
  ;; curl -X GET --location "http://localhost:8080/country/IRL/name"
)