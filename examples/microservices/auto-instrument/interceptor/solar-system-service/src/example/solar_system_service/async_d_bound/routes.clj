(ns example.solar-system-service.async-d-bound.routes
  "HTTP routes, Manifold implementation using bound context."
  (:require [example.common.async.manifold :as d']
            [example.solar-system-service.async-d-bound.app :as app]
            [manifold.deferred :as d]
            [ring.util.response :as response]
            [steffan-westcott.clj-otel.api.trace.d-span :as d-span]))


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
            (-> (d-span/async-bound-d-span "Handling route"

                                           (let [planet (keyword (get query-params :planet))]
                                             (-> (app/<planet-report components planet)
                                                 (d/chain' (fn [stats]
                                                             (response/response {:statistics
                                                                                 stats}))))))
                (d'/<d-response ctx)))})



(defn routes
  "Routes for the service."
  []
  #{["/ping" :get get-ping] ;
    ["/statistics" :get get-statistics]})
