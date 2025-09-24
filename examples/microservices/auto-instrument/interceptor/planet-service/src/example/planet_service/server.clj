(ns example.planet-service.server
  "HTTP server components."
  (:require [example.common.async.interceptor :as common-interceptor]
            [example.planet-service.env :refer [config]]
            [example.planet-service.routes :as routes]
            [io.pedestal.connector :as conn]
            [io.pedestal.http.body-params :as body-params]
            [io.pedestal.http.jetty :as jetty]
            [io.pedestal.http.route :as route]
            [steffan-westcott.clj-otel.api.trace.http :as trace-http]))


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
          (trace-http/server-span-interceptors)

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
        (conn/with-routes (routes/routes))

        ;; Adds matched route data to server spans
        (conn/with-interceptor (trace-http/route-interceptor))

        (jetty/create-connector jetty-options)
        (conn/start!))))



(defn stop-connector
  "Stops the Jetty server."
  [connector]
  (conn/stop! connector))
