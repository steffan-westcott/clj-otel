(ns tutorial.counter-service
  "Instrumented version of tutorial counter-service application."
  (:require [ring.adapter.jetty :as jetty]
            [ring.middleware.params :as params]
            [ring.util.response :as response]
            [steffan-westcott.clj-otel.api.metrics.counter :as counter]
            [steffan-westcott.clj-otel.api.metrics.gauge :as gauge]
            [steffan-westcott.clj-otel.api.metrics.histogram :as histogram]
            [steffan-westcott.clj-otel.api.trace.http :as trace-http]
            [steffan-westcott.clj-otel.api.trace.span :as span])
  (:import (io.opentelemetry.api GlobalOpenTelemetry)))


(defonce ^{:doc "Global Meter instance for Otel"} otel-meter
         (GlobalOpenTelemetry/getMeter "clj-otel.tutorial.counter-service"))


(defonce
 ^{:doc "Counter state"} counter
 (atom
  {:val       0

   :counter   (counter/long-counter otel-meter
                                    "clj-otel.tutorial.counter-service.http-request-counter"
                                    "counter for http requests"
                                    "nos")

   :gauge     (gauge/double-gauge otel-meter
                                  "clj-otel.tutorial.counter-service.counter-gauge"
                                  (fn []
                                    (:counter-gauge @counter))

                                  "value of the counter" "nos")

   :histogram (histogram/double-histogram otel-meter
                                          "clj-otel.tutorial.counter-service.reset-duration-seconds"
                                          "duration of reset handler"
                                          "seconds")}))


(defn wrap-exception
  "Ring middleware for wrapping an exception as an HTTP 500 response."
  [handler]
  (fn [request]
    (try
      (handler request)
      (catch Throwable e
        (span/add-exception! e {:escaping? false})
        (let [resp (response/response (ex-message e))]
          (response/status resp 500))))))


(defn reset-count-handler
  "Ring handler for 'PUT /reset' request. Resets counter, returns HTTP 204."
  [{:keys [query-params]}]
  (let [n (Integer/parseInt (get query-params "n"))]
    (swap! counter assoc
      :val
      n)
    (histogram/record-histogram! (:histogram @counter)
                                 (rand 10)
                                 {:status (rand-nth [:success :failure :unknown])})
    (response/status 204)))


(defn get-count-handler
  "Ring handler for 'GET /count' request. Returns an HTTP response with counter
  value."
  []
  (let [n (:val @counter)]
    (span/add-span-data! {:attributes {:service.counter/count n}})
    (swap! counter assoc
      :counter-gauge
      n)
    (response/response (str n))))


(defn inc-count-handler
  "Ring handler for 'POST /inc' request. Increments counter, returns HTTP 204."
  []
  (span/with-span! {:name "Incrementing counter"}
    (swap! counter update
      :val
      inc))
  (response/status 204))


(defn handler
  "Ring handler for all requests."
  [{:keys [request-method uri]
    :as   request}]
  (counter/inc-counter! (:counter @counter))
  (case [request-method uri]
    [:put "/reset"] (reset-count-handler request)
    [:get "/count"] (get-count-handler)
    [:post "/inc"]  (inc-count-handler)
    (response/not-found "Not found")))


(def service
  "Ring handler with middleware applied."
  (-> handler
      params/wrap-params
      wrap-exception
      (trace-http/wrap-server-span)))


(defonce ^{:doc "counter-service server instance"} server
         (jetty/run-jetty #'service
                          {:port  8080
                           :join? false}))
