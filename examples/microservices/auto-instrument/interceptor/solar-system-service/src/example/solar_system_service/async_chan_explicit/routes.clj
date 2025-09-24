(ns example.solar-system-service.async-chan-explicit.routes
  "HTTP routes, core.async implementation using explicit context."
  (:require [com.xadecimal.async-style :as style]
            [example.common.async.async-style :as style']
            [example.solar-system-service.async-chan-explicit.app :as app]
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
            (-> (style'/route-span-binding [context ctx]

                  (let [planet (keyword (get query-params :planet))]
                    (-> (app/<planet-report components context planet)
                        (style/then #(response/response {:statistics %})))))
                (style'/<assoc-response ctx)))})



(defn routes
  "Routes for the service."
  []
  #{["/ping" :get get-ping] ;
    ["/statistics" :get get-statistics]})
