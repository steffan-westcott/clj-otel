(ns example.average-service.sync.routes
  "HTTP routes, synchronous implementation."
  (:require [clojure.string :as str]
            [example.average-service.sync.app :as app]
            [io.pedestal.http.route :as route]
            [ring.util.response :as response]
            [steffan-westcott.clj-otel.api.trace.span :as span]))


(defn get-ping
  "Returns an empty response."
  [_]
  (response/response nil))



(defn get-average
  "Returns an HTTP response containing calculated averages of the odd and even numbers."
  [request]
  (span/with-span! "Handling route"
    (let [{:keys [components query-params]} request
          num-str  (get query-params :nums)
          num-strs (->> (str/split num-str #",")
                        (map str/trim)
                        (filter seq))
          nums     (map #(Integer/parseInt %) num-strs)
          avs      (app/averages components nums)]
      (response/response {:average avs}))))



(defn routes
  "Routes for the service."
  []
  (route/expand-routes
   #{["/ping" :get `get-ping] ;
     ["/average" :get `get-average]}))
