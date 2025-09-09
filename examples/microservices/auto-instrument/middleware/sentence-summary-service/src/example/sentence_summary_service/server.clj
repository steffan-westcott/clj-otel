(ns example.sentence-summary-service.server
  "HTTP server and handler components."
  (:require [example.sentence-summary-service.async-cf-bound.routes :as async-cf-bound-routes]
            [example.sentence-summary-service.async-cf-explicit.routes :as async-cf-explicit-routes]
            [example.sentence-summary-service.async-chan-bound.routes :as async-chan-bound-routes]
            [example.sentence-summary-service.async-chan-explicit.routes :as
             async-chan-explicit-routes]
            [example.sentence-summary-service.async-d-bound.routes :as async-d-bound-routes]
            [example.sentence-summary-service.async-d-explicit.routes :as async-d-explicit-routes]
            [example.sentence-summary-service.env :refer [config]]
            [example.sentence-summary-service.sync.routes :as sync-routes]
            [muuntaja.core :as m]
            [reitit.coercion.malli :as coercion-malli]
            [reitit.ring :as ring]
            [reitit.ring.coercion :as coercion]
            [reitit.ring.middleware.exception :as exception]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [reitit.ring.middleware.parameters :as parameters]
            [ring.adapter.jetty9 :as jetty]
            [steffan-westcott.clj-otel.api.trace.http :as trace-http]
            [steffan-westcott.clj-otel.api.trace.span :as span])
  (:import (org.eclipse.jetty.server Server)))


(defn- async?
  []
  (not= "sync" (:server-impl config)))


(defn- using-bound-context?
  []
  (boolean (#{"async-cf-bound" "async-chan-bound" "async-d-bound"} (:server-impl config))))



(defn- routes
  "Route data for all routes, according to configured server implementation."
  [components]
  (case (:server-impl config)
    "async-cf-bound"    (async-cf-bound-routes/routes components)
    "async-cf-explicit" (async-cf-explicit-routes/routes components)
    "async-chan-bound"  (async-chan-bound-routes/routes components)
    "async-chan-explicit" (async-chan-explicit-routes/routes components)
    "async-d-bound"     (async-d-bound-routes/routes components)
    "async-d-explicit"  (async-d-explicit-routes/routes components)
    "sync"              (sync-routes/routes components)))



(defn- router
  "Returns a Reitit Ring router. The matched Reitit route is added to the
   server span data."
  [components]
  (ring/router (routes components)
               {:data {:muuntaja   m/instance
                       :coercion   coercion-malli/coercion
                       :middleware [;; Add route data to server span
                                    trace-http/wrap-reitit-route ;

                                    muuntaja/format-negotiate-middleware ;
                                    muuntaja/format-response-middleware ;
                                    exception/exception-middleware ;

                                    ;; Ensure uncaught exceptions are recorded before
                                    ;; they are transformed
                                    (if (using-bound-context?)
                                      span/wrap-bound-span
                                      span/wrap-span)

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

                     ;; Wrap handling of all requests, including those which have no
                     ;; matching route. As this application is run with the OpenTelemetry
                     ;; instrumentation agent, a server span will be provided by the agent
                     ;; and there is no need to create another one.
                     {:middleware [[trace-http/wrap-server-span {:create-span? false}]]}))



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
  (jetty/run-jetty handler (assoc (:jetty config) :h2c? true :join? false :async? (async?))))



(defn stop-server
  "Stops the given server."
  [^Server server]
  (.stop server))
