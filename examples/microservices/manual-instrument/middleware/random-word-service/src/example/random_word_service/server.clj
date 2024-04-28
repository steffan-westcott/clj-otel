(ns example.random-word-service.server
  "HTTP server and handler components."
  (:require [example.random-word-service.env :refer [config]]
            [example.random-word-service.routes :as routes]
            [muuntaja.core :as m]
            [reitit.coercion.malli :as coercion-malli]
            [reitit.ring :as ring]
            [reitit.ring.coercion :as coercion]
            [reitit.ring.middleware.exception :as exception]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [reitit.ring.middleware.parameters :as parameters]
            [ring.adapter.jetty :as jetty]
            [steffan-westcott.clj-otel.api.metrics.http.server :as metrics-http-server]
            [steffan-westcott.clj-otel.api.trace.http :as trace-http])
  (:import (org.eclipse.jetty.server Server)))


(defn router
  "Returns a Reitit Ring router. The matched Reitit route is added to the
   server span data."
  [components]
  (ring/router (routes/routes components)
               {:data {:muuntaja   m/instance
                       :coercion   coercion-malli/coercion
                       :middleware [;; Add route data to server span
                                    trace-http/wrap-reitit-route ;

                                    ;; Add metrics that include http.route attribute
                                    metrics-http-server/wrap-metrics-by-route

                                    muuntaja/format-negotiate-middleware ;
                                    muuntaja/format-response-middleware ;
                                    exception/exception-middleware ;

                                    ;; Add exception event before
                                    ;; exception/exception-middleware runs
                                    trace-http/wrap-exception-event ;

                                    parameters/parameters-middleware ;
                                    muuntaja/format-request-middleware ;
                                    coercion/coerce-response-middleware ;
                                    coercion/coerce-request-middleware ;
                                   ]}}))



(defn handler
  "Returns a Ring handler with server span support. Routes are passed a map of
   stateful `components`."
  [components]
  (ring/ring-handler (router components)
                     (ring/create-default-handler)

                     ;; Wrap handling of all requests, including those which have no matching
                     ;; route. As this application is not run with the OpenTelemetry
                     ;; instrumentation agent, create a server span for each request.
                     {:middleware [[trace-http/wrap-server-span {:create-span? true}]
                                   [metrics-http-server/wrap-active-requests]]}))



(defn rebuilding-handler
  "Returns same as `handler` but also rebuilds the router on every request.
   This is intended for modification of `routes` in REPL based development."
  [components]
  (fn [request]
    ((handler components) request)))



(defn server
  "Starts and returns a synchronous HTTP server."
  [handler]
  (jetty/run-jetty handler (assoc (:jetty config) :join? false)))



(defn stop-server
  "Stops the given server."
  [^Server server]
  (.stop server))
