(ns example.average-service.explicit-async.routes
  "HTTP routes, explicit async implementation."
  (:require [clojure.string :as str]
            [example.average-service.explicit-async.app :as app]
            [example.common.core-async.utils :as async']
            [io.pedestal.http.route :as route]
            [ring.util.response :as response]))


(defn get-ping
  "Returns an empty response."
  [_]
  (response/response nil))



(def get-average
  "Interceptor that returns a channel of an HTTP response containing calculated
   averages of the odd and even numbers."
  {:name  ::get-average
   :enter (fn [{:keys [io.opentelemetry/server-span-context request]
                :as   ctx}]
            (let [num-str  (get-in request [:query-params :nums])
                  num-strs (->> (str/split num-str #",")
                                (map str/trim)
                                (filter seq))
                  nums     (map #(Integer/parseInt %) num-strs)
                  <avs     (app/<averages (:components request) server-span-context nums)]
              (async'/go-try-response ctx
                (let [avs (async'/<? <avs)]
                  (response/response {:average avs})))))})



(defn routes
  "Routes for the service."
  []
  (route/expand-routes
   #{["/ping" :get `get-ping] ;
     ["/average" :get `get-average]}))
