(ns tutorial.counter-service
  (:require [ring.adapter.jetty :as jetty]
            [ring.middleware.params :as params]
            [ring.util.response :as response]))


(def counter
  "Counter state"
  (atom 0))


(defn wrap-exception
  "Ring middleware for wrapping an exception as an HTTP 500 response."
  [handler]
  (fn [request]
    (try
      (handler request)
      (catch Throwable e
        (let [resp (response/response (ex-message e))]
          (response/status resp 500))))))


(defn get-count-handler
  "Ring handler for 'GET /count' request. Returns an HTTP response with counter
  value."
  []
  (response/response (str @counter)))


(defn inc-count-handler
  "Ring handler for 'POST /inc' request. Increments counter, returns HTTP 204."
  []
  (swap! counter inc)
  (response/status 204))


(defn reset-count-handler
  "Ring handler for 'PUT /reset' request. Resets counter, returns HTTP 200."
  [{:keys [query-params]}]
  (let [n (Integer/parseInt (get query-params "n"))]
    (reset! counter n)
    (response/response nil)))


(defn handler
  "Ring handler for all requests."
  [{:keys [request-method uri] :as request}]
  (case [request-method uri]
    [:get "/count"] (get-count-handler)
    [:post "/inc"] (inc-count-handler)
    [:put "/reset"] (reset-count-handler request)
    (response/not-found "Not found")))


(def service
  "Ring handler with middleware applied."
  (-> handler
      params/wrap-params
      wrap-exception))


(defonce ^{:doc "counter-service server instance"} server
         (jetty/run-jetty #'service {:port 8080 :join? false}))