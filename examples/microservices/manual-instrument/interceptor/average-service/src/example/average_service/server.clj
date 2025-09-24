(ns example.average-service.server
  "HTTP server components."
  (:require [example.average-service.async-cf-bound.routes :as async-cf-bound-routes]
            [example.average-service.async-cf-explicit.routes :as async-cf-explicit-routes]
            [example.average-service.async-chan-bound.routes :as async-chan-bound-routes]
            [example.average-service.async-chan-explicit.routes :as async-chan-explicit-routes]
            [example.average-service.async-d-bound.routes :as async-d-bound-routes]
            [example.average-service.async-d-explicit.routes :as async-d-explicit-routes]
            [example.average-service.async-task-explicit.routes :as async-task-explicit-routes]
            [example.average-service.env :refer [config]]
            [example.average-service.sync.routes :as sync-routes]
            [example.common.async.interceptor :as common-interceptor]
            [io.pedestal.connector :as conn]
            [io.pedestal.http.body-params :as body-params]
            [io.pedestal.http.jetty :as jetty]
            [io.pedestal.http.route :as route]
            [steffan-westcott.clj-otel.api.metrics.http.server :as metrics-http-server]
            [steffan-westcott.clj-otel.api.trace.http :as trace-http]))


(defn- using-bound-context?
  []
  (boolean (#{"async-cf-bound" "async-chan-bound" "async-d-bound"} (:server-impl config))))



(defn- sync?
  []
  (= "sync" (:server-impl config)))



(defn- routes
  "Route data for all routes, according to configured server implementation."
  []
  (case (:server-impl config)
    "async-cf-bound"    (async-cf-bound-routes/routes)
    "async-cf-explicit" (async-cf-explicit-routes/routes)
    "async-chan-bound"  (async-chan-bound-routes/routes)
    "async-chan-explicit" (async-chan-explicit-routes/routes)
    "async-d-bound"     (async-d-bound-routes/routes)
    "async-d-explicit"  (async-d-explicit-routes/routes)
    "async-task-explicit" (async-task-explicit-routes/routes)
    "sync"              (sync-routes/routes)))



(defn connector
  "Starts the Jetty server and returns the started connector."
  [components]
  (let [{{:keys [host port jetty-options]} :connector} config]
    (-> (conn/default-connector-map host port)
        (conn/with-interceptors ;
         (concat

          ;; As this application is not run with the OpenTelemetry instrumentation
          ;; agent, create a server span for each request. The current context is
          ;; set if all request handling is processed synchronously.
          (trace-http/server-span-interceptors {:create-span?         true
                                                :set-current-context? (sync?)
                                                :set-bound-context?   (using-bound-context?)})

          ;; Add metric that records the number of active HTTP requests
          [(metrics-http-server/active-requests-interceptor)]

          ;; Negotiate content formats
          [(common-interceptor/content-negotiation-interceptor)]

          ;; Coerce HTTP response format
          [(common-interceptor/coerce-response-interceptor)]

          ;; 404 Not Found response
          [(common-interceptor/not-found-interceptor)]

          ;; Parse query string parameters
          [route/query-params]

          ;; Parse request body
          [(body-params/body-params)]

          ;; Convert exception to HTTP response
          [(common-interceptor/exception-response-interceptor)]

          ;; Add system components to context
          [(common-interceptor/components-interceptor components)]))

        ;; Match route and add `:route` key to ctx
        (conn/with-routes (routes))

        (conn/with-interceptors ;
         (concat

          ;; Adds matched route data to server spans
          [(trace-http/route-interceptor)]

          ;; Adds metrics that include http.route attribute
          (metrics-http-server/metrics-by-route-interceptors)))

        (jetty/create-connector jetty-options)
        (conn/start!))))



(defn stop-connector
  "Stops the Jetty server."
  [connector]
  (conn/stop! connector))
