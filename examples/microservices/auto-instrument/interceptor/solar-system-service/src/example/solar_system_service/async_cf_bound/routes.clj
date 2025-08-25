(ns example.solar-system-service.async-cf-bound.routes
  "HTTP routes, async CompletableFuture implementation using bound context."
  (:require [example.common.async.auspex :as aus']
            [example.solar-system-service.async-cf-bound.app :as app]
            [io.pedestal.http.route :as route]
            [qbits.auspex :as aus]
            [ring.util.response :as response]
            [steffan-westcott.clj-otel.api.trace.span :as span]))


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
            (-> (span/async-bound-cf-span "Handling route"

                                          (let [planet (keyword (get query-params :planet))]
                                            (-> (app/<planet-report components planet)
                                                (aus/then (bound-fn [stats]
                                                            (response/response {:statistics
                                                                                stats}))))))
                (aus'/<cf-response ctx)))})



(defn routes
  "Routes for the service."
  []
  (route/expand-routes
   #{["/ping" :get `get-ping] ;
     ["/statistics" :get `get-statistics]}))
