(ns example.solar-system-service.server
  "HTTP server components."
  (:require [example.common.async.interceptor :as common-interceptor]
            [example.solar-system-service.async-cf-bound.routes :as async-cf-bound-routes]
            [example.solar-system-service.async-cf-explicit.routes :as async-cf-explicit-routes]
            [example.solar-system-service.async-chan-bound.routes :as async-chan-bound-routes]
            [example.solar-system-service.async-chan-explicit.routes :as async-chan-explicit-routes]
            [example.solar-system-service.async-d-bound.routes :as async-d-bound-routes]
            [example.solar-system-service.async-d-explicit.routes :as async-d-explicit-routes]
            [example.solar-system-service.async-task-explicit.routes :as async-task-explicit-routes]
            [example.solar-system-service.env :refer [config]]
            [example.solar-system-service.sync.routes :as sync-routes]
            [io.pedestal.connector :as conn]
            [io.pedestal.http.body-params :as body-params]
            [io.pedestal.http.jetty :as jetty]
            [io.pedestal.http.route :as route]
            [steffan-westcott.clj-otel.api.trace.http :as trace-http]))


(defn- using-bound-context?
  []
  (boolean (#{"async-cf-bound" "async-chan-bound" "async-d-bound"} (:server-impl config))))



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

          ;; As this application is run with the OpenTelemetry instrumentation agent,
          ;; a server span will be provided by the agent and there is no need to
          ;; create another one.
          (trace-http/server-span-interceptors {:set-bound-context? (using-bound-context?)})

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

        ;; Adds matched route data to server spans
        (conn/with-interceptor (trace-http/route-interceptor))

        (jetty/create-connector jetty-options)
        (conn/start!))))



(defn stop-connector
  "Stops the Jetty server."
  [connector]
  (conn/stop! connector))
