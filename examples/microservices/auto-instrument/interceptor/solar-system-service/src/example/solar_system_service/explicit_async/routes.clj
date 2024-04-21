(ns example.solar-system-service.explicit-async.routes
  "HTTP routes, explicit async implementation."
  (:require [example.common.core-async.utils :as async']
            [example.solar-system-service.explicit-async.app :as app]
            [io.pedestal.http.route :as route]
            [ring.util.response :as response]))


(defn get-ping
  "Returns an empty response."
  [_]
  (response/response nil))



(def get-statistics
  "Interceptor that returns a channel of the HTTP response containing a
   formatted report of the planet's statistic values."
  {:name  ::get-statistics
   :enter (fn [{:keys [io.opentelemetry/server-span-context request]
                :as   ctx}]
            (let [planet  (keyword (get-in request [:query-params :planet]))
                  <report (app/<planet-report (:components request) server-span-context planet)]
              (async'/go-try-response ctx
                (let [report (async'/<? <report)]
                  (response/response {:statistics report})))))})



(defn routes
  "Route maps for the service."
  []
  (route/expand-routes
   #{["/ping" :get `get-ping] ;
     ["/statistics" :get `get-statistics]}))
