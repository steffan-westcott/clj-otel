(ns example.average-service.async-task-explicit.routes
  "HTTP routes, Missionary implementation using explicit context."
  (:require [clojure.string :as str]
            [example.average-service.async-task-explicit.app :as app]
            [example.common.async.missionary :as m']
            [io.pedestal.http.route :as route]
            [missionary.core :as m]
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
            (-> (m'/route-span-binding [context ctx]

                  (m/sp
                    (let [num-str  (get query-params :nums)
                          num-strs (->> (str/split num-str #",")
                                        (map str/trim)
                                        (filter seq))
                          nums     (map #(Integer/parseInt %) num-strs)
                          averages (m/? (app/<averages components context nums))]
                      (response/response {:average averages}))))
                (m'/<assoc-response ctx)))})



(defn routes
  "Routes for the service."
  []
  (route/expand-routes
   #{["/ping" :get `get-ping] ;
     ["/average" :get `get-average]}))

