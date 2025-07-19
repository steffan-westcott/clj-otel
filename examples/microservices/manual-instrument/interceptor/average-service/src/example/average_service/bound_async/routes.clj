(ns example.average-service.bound-async.routes
  "HTTP routes, bound async implementation."
  (:require [clojure.string :as str]
            [com.xadecimal.async-style :as style]
            [example.average-service.bound-async.app :as app]
            [example.common.async-style.utils :as style']
            [io.pedestal.http.route :as route]
            [ring.util.response :as response]))


(defn get-ping
  "Returns an empty response."
  [_]
  (response/response nil))



(def get-average
  "Interceptor that returns a channel of ctx with response containing
   calculated averages of the odd and even numbers."
  {:name  ::get-average
   :enter (fn [{:keys [request]
                :as   ctx}]
            (let [num-str  (get-in request [:query-params :nums])
                  num-strs (->> (str/split num-str #",")
                                (map str/trim)
                                (filter seq))
                  nums     (map #(Integer/parseInt %) num-strs)]
              (-> (app/<averages (:components request) nums)
                  (style/then #(response/response {:average %}))
                  (style'/<assoc-response ctx))))})



(defn routes
  "Routes for the service."
  []
  (route/expand-routes
   #{["/ping" :get `get-ping] ;
     ["/average" :get `get-average]}))
