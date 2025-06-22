(ns example.sum-service.routes
  "HTTP routes."
  (:require [clojure.string :as str]
            [example.sum-service.app :as app]
            [io.pedestal.http.route :as route]
            [ring.util.response :as response]))


(defn get-ping
  "Handler for ping health check"
  [_]
  (response/response nil))



(defn get-sum
  "Returns a response containing the sum of the `nums` query parameters."
  [{:keys [components query-params]}]
  (let [num-str (:nums query-params)
        nums    (map #(Integer/parseInt %) (str/split num-str #","))]

    ;; Simulate a client error when first number argument is zero. Exception data is added as
    ;; attributes to the exception event by default.
    (if (= 0 (first nums))
      (throw (ex-info "Zero argument"
                      {:http.response/status 400
                       :system/error         :service.sum.errors/zero-argument}))
      (response/response {:sum (app/sum components nums)}))))



(defn routes
  "Route maps for the service."
  []
  (route/expand-routes
   #{["/ping" :get `get-ping] ;
     ["/sum" :get `get-sum]}))
