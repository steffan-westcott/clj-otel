(ns example.solar-system-service.async-task-explicit.routes
  "HTTP routes, Missionary implementation using explicit context."
  (:require [example.common.async.missionary :as m']
            [example.solar-system-service.async-task-explicit.app :as app]
            [missionary.core :as m]
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
            (-> (m'/route-span-binding [context ctx]

                  (m/sp
                    (let [planet     (keyword (get query-params :planet))
                          statistics (m/? (app/<planet-report components context planet))]
                      (response/response {:statistics statistics}))))
                (m'/<assoc-response ctx)))})



(defn routes
  "Routes for the service."
  []
  #{["/ping" :get get-ping] ;
    ["/statistics" :get get-statistics]})

