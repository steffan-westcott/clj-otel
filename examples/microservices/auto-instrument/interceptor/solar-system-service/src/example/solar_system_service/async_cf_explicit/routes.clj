(ns example.solar-system-service.async-cf-explicit.routes
  "HTTP routes, async CompletableFuture implementation using explicit context."
  (:require [example.common.async.auspex :as aus']
            [example.solar-system-service.async-cf-explicit.app :as app]
            [qbits.auspex :as aus]
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
            (-> (aus'/route-span-binding [context ctx]

                  (let [planet (keyword (get query-params :planet))]
                    (-> (app/<planet-report components context planet)
                        (aus/then (fn [stats]
                                    (response/response {:statistics stats}))))))
                (aus'/<cf-response ctx)))})



(defn routes
  "Routes for the service."
  []
  #{["/ping" :get get-ping] ;
    ["/statistics" :get get-statistics]})

