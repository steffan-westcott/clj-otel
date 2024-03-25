(ns example.solar-system-service.bound-async.routes
  "HTTP routes, bound async implementation."
  (:require [example.common.core-async.utils :as async']
            [example.solar-system-service.bound-async.app :as app]
            [io.pedestal.http.route :as route]
            [ring.util.response :as response]))


(defn get-ping
  "Returns an empty response."
  [_]
  (response/response nil))



(def get-statistics
  "Interceptor that returns a channel of an HTTP response containing a
   formatted report of the planet's statistic values."
  {:name  ::get-statistics
   :enter (fn [{:keys [request]
                :as   ctx}]
            (let [planet  (keyword (get-in request [:query-params :planet]))
                  <report (app/<planet-report (:components request) planet)]
              (async'/go-try-response ctx
                (let [report (async'/<? <report)]
                  (response/response report)))))})



(defn routes
  "Routes for the service."
  []
  (route/expand-routes
   #{["/ping" :get `get-ping] ;
     ["/statistics" :get `get-statistics]}))
