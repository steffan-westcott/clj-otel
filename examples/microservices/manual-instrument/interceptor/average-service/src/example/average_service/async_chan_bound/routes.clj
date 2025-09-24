(ns example.average-service.async-chan-bound.routes
  "HTTP routes, core.async implementation using bound context."
  (:require [clojure.string :as str]
            [com.xadecimal.async-style :as style]
            [example.average-service.async-chan-bound.app :as app]
            [example.common.async.async-style :as style']
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
            (-> (style'/route-bound-span

                  (let [num-str  (get query-params :nums)
                        num-strs (->> (str/split num-str #",")
                                      (map str/trim)
                                      (filter seq))
                        nums     (map #(Integer/parseInt %) num-strs)]
                    (-> (app/<averages components nums)
                        (style/then #(response/response {:average %})))))
                (style'/<assoc-response ctx)))})



(defn routes
  "Routes for the service."
  []
  #{["/ping" :get get-ping] ;
    ["/average" :get get-average]})
