(ns example.average-service.async-cf-bound.routes
  "HTTP routes, async CompletableFuture implementation using bound context."
  (:require [clojure.string :as str]
            [example.average-service.async-cf-bound.app :as app]
            [example.common.async.auspex :as aus']
            [io.pedestal.http.route :as route]
            [qbits.auspex :as aus]
            [ring.util.response :as response]
            [steffan-westcott.clj-otel.api.trace.span :as span]))


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
            (-> (span/async-bound-cf-span "Handling route"

                                          (let [num-str  (get query-params :nums)
                                                num-strs (->> (str/split num-str #",")
                                                              (map str/trim)
                                                              (filter seq))
                                                nums     (map #(Integer/parseInt %) num-strs)]
                                            (-> (app/<averages components nums)
                                                (aus/then (bound-fn [averages]
                                                            (response/response {:average
                                                                                averages}))))))
                (aus'/<cf-response ctx)))})



(defn routes
  "Routes for the service."
  []
  (route/expand-routes
   #{["/ping" :get `get-ping] ;
     ["/average" :get `get-average]}))
