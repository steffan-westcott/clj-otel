(ns example.average-service.async-d-explicit.routes
  "HTTP routes, Manifold implementation using explicit context."
  (:require [clojure.string :as str]
            [example.average-service.async-d-explicit.app :as app]
            [example.common.async.manifold :as d']
            [io.pedestal.http.route :as route]
            [manifold.deferred :as d]
            [ring.util.response :as response]))


(defn get-ping
  "Returns an empty response."
  [_]
  (response/response nil))



(def get-average
  "Interceptor that returns a channel of ctx with response containing
   calculated averages of the odd and even numbers."
  {:name  ::get-average
   :enter (fn [{{:keys [components query-params]} :request
                :as ctx}]

            ;; Ensure uncaught exceptions are recorded before they are transformed
            (-> (d'/route-span-binding [context ctx]

                  (let [num-str  (get query-params :nums)
                        num-strs (->> (str/split num-str #",")
                                      (map str/trim)
                                      (filter seq))
                        nums     (map #(Integer/parseInt %) num-strs)]
                    (-> (app/<averages components context nums)
                        (d/chain' (fn [averages]
                                    (response/response {:average averages}))))))
                (d'/<d-response ctx)))})



(defn routes
  "Routes for the service."
  []
  (route/expand-routes
   #{["/ping" :get `get-ping] ;
     ["/average" :get `get-average]}))

