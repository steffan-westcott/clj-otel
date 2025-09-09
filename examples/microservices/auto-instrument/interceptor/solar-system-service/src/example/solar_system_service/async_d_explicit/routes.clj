(ns example.solar-system-service.async-d-explicit.routes
  "HTTP routes, Manifold implementation using explicit context."
  (:require [example.common.async.manifold :as d']
            [example.solar-system-service.async-d-explicit.app :as app]
            [io.pedestal.http.route :as route]
            [manifold.deferred :as d]
            [ring.util.response :as response]))


(defn get-ping
  "Returns an empty response."
  [_]
  (response/response nil))



(def get-statistics
  "Interceptor that returns a channel of ctx with response containing a
   formatted report of the planet's statistic values."
  {:name  ::get-statistics
   :enter (fn [{{:keys [components query-params]} :request
                :as ctx}]

            ;; Ensure uncaught exceptions are recorded before they are transformed
            (-> (d'/route-span-binding [context ctx]

                  (let [planet (keyword (get query-params :planet))]
                    (-> (app/<planet-report components context planet)
                        (d/chain' (fn [stats]
                                    (response/response {:statistics stats}))))))
                (d'/<d-response ctx)))})



(defn routes
  "Routes for the service."
  []
  (route/expand-routes
   #{["/ping" :get `get-ping] ;
     ["/statistics" :get `get-statistics]}))
