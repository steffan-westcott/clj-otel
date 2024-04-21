(ns example.solar-system-service.sync.routes
  "HTTP routes, synchronous implementation."
  (:require [example.solar-system-service.sync.app :as app]
            [io.pedestal.http.route :as route]
            [ring.util.response :as response]))


(defn get-ping
  "Returns an empty response."
  [_]
  (response/response nil))



(defn get-statistics
  "Returns a response containing a formatted report of the planet's statistic values."
  [{:keys [components query-params]}]
  (let [planet (keyword (get query-params :planet))
        report (app/planet-report components planet)]
    (response/response {:statistics report})))



(defn routes
  "Routes for the service."
  []
  (route/expand-routes
   #{["/ping" :get `get-ping] ;
     ["/statistics" :get `get-statistics]}))
