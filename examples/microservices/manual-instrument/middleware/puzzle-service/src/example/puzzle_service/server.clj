(ns example.puzzle-service.server
  "HTTP server and handler components."
  (:require [example.puzzle-service.bound-async.routes :as bound-async-routes]
            [example.puzzle-service.env :refer [config]]
            [example.puzzle-service.explicit-async.routes :as explicit-async-routes]
            [example.puzzle-service.sync.routes :as sync-routes]
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


(defn- async?
  []
  (case (:server-impl config)
    "sync"           false
    "bound-async"    true
    "explicit-async" true))



(defn- routes
  "Route data for all routes, according to configured server implementation."
  [components]
  (case (:server-impl config)
    "sync"           (sync-routes/routes components)
    "bound-async"    (bound-async-routes/routes components)
    "explicit-async" (explicit-async-routes/routes components)))



(defn- router
  "Returns a Reitit Ring router. The matched Reitit route is added to the
   server span data."
  [components]
  (ring/router (routes components)
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

                     ;; Ensure metrics for default handler
                     (-> (ring/create-default-handler)
                         metrics-http-server/wrap-metrics-by-route)

                     ;; Wrap handling of all requests, including those which have no matching
                     ;; route. As this application is not run with the OpenTelemetry
                     ;; instrumentation agent, create a server span for each request.
                     {:middleware [[trace-http/wrap-server-span {:create-span? true}]
                                   [metrics-http-server/wrap-active-requests]]}))



(defn rebuilding-handler
  "Returns same as `handler` but also rebuilds the router on every request.
   This is intended for modification of `routes` in REPL based development."
  [components]
  (fn
    ([request]
     ((handler components) request))
    ([request respond raise]
     ((handler components) request respond raise))))



(defn server
  "Starts and returns an (a)synchronous HTTP server."
  [handler]
  (jetty/run-jetty handler (assoc (:jetty config) :join? false :async? (async?))))



(defn stop-server
  "Stops the given server."
  [^Server server]
  (.stop server))
