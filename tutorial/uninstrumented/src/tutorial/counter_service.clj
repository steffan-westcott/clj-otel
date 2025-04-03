(ns tutorial.counter-service
  "Uninstrumented version of tutorial counter-service application."
  (:require [ring.adapter.jetty :as jetty]
            [ring.middleware.params :as params]
            [ring.util.response :as response]))


(defonce ^{:doc "Counter state"} counter
  (atom 0))



(defn reset-count-handler
  "Ring handler for 'PUT /reset' request. Resets counter, returns HTTP 204."
  [{:keys [query-params]}]
  (let [n (Integer/parseInt (get query-params "n"))]
    (reset! counter n)
    (response/status 204)))


(defn get-count-handler
  "Ring handler for 'GET /count' request. Returns an HTTP response with counter
   value."
  []
  (let [n @counter]
    (response/response (str n))))


(defn inc-count-handler
  "Ring handler for 'POST /inc' request. Increments counter, returns HTTP 204."
  []
  (swap! counter inc)
  (response/status 204))


(defn handler
  "Ring handler for all requests."
  [{:keys [request-method uri]
    :as   request}]
  (case [request-method uri]
    [:put "/reset"] (reset-count-handler request)
    [:get "/count"] (get-count-handler)
    [:post "/inc"]  (inc-count-handler)
    (response/not-found "Not found")))


(def service
  "Ring handler with middleware applied."
  (-> handler
      params/wrap-params))


(defonce ^{:doc "counter-service server instance"} server
  (jetty/run-jetty #'service
                   {:port  8080
                    :join? false}))