(ns example.average-service.server
  "HTTP server components."
  (:require [example.average-service.async-cf-bound.routes :as async-cf-bound-routes]
            [example.average-service.async-cf-explicit.routes :as async-cf-explicit-routes]
            [example.average-service.bound-async.routes :as bound-async-routes]
            [example.average-service.env :refer [config]]
            [example.average-service.explicit-async.routes :as explicit-async-routes]
            [example.average-service.sync.routes :as sync-routes]
            [example.common.async.interceptor :as common-interceptor]
            [io.pedestal.http :as http]
            [io.pedestal.interceptor :as interceptor]
            [steffan-westcott.clj-otel.api.metrics.http.server :as metrics-http-server]
            [steffan-westcott.clj-otel.api.trace.http :as trace-http]))


(defn- using-bound-context?
  []
  (boolean (#{"bound-async" "async-cf-bound"} (:server-impl config))))



(defn- sync?
  []
  (= "sync" (:server-impl config)))



(defn- routes
  "Route data for all routes, according to configured server implementation."
  []
  (case (:server-impl config)
    "sync"              (sync-routes/routes)
    "bound-async"       (bound-async-routes/routes)
    "explicit-async"    (explicit-async-routes/routes)
    "async-cf-bound"    (async-cf-bound-routes/routes)
    "async-cf-explicit" (async-cf-explicit-routes/routes)))



(defn- update-interceptors
  "Returns modified default interceptors."
  [default-interceptors components]
  (map interceptor/interceptor
       (concat (;; As this application is not run with the OpenTelemetry instrumentation
                ;; agent, create a server span for each request. The current context is
                ;; set if all request handling is processed synchronously.
                trace-http/server-span-interceptors {:create-span?         true
                                                     :set-current-context? (sync?)
                                                     :set-bound-context?   (using-bound-context?)})

               ;; Add metric that records the number of active HTTP requests
               [(metrics-http-server/active-requests-interceptor)]

               ;; Negotiate content formats
               [(common-interceptor/content-negotiation-interceptor)]

               ;; Coerce HTTP response format
               [(common-interceptor/coerce-response-interceptor)]

               ;; Default Pedestal interceptor stack
               default-interceptors

               ;; Adds matched route data to server spans
               [(trace-http/route-interceptor)]

               ;; Adds metrics that include http.route attribute
               (metrics-http-server/metrics-by-route-interceptors)

               ;; Convert exception to HTTP response
               [(common-interceptor/exception-response-interceptor)]

               ;; Add system components to context
               [(common-interceptor/components-interceptor components)])))



(defn service-map
  "Returns a service map ready for creating an HTTP server."
  [components]
  (-> {::http/routes  #(routes) ; rebuild routes on every request
       ::http/type    :jetty
       ::http/host    "0.0.0.0"
       ::http/join?   false
       ::http/not-found-interceptor (common-interceptor/not-found-interceptor)
       ::http/tracing nil}
      (merge (:service-map config))
      (http/default-interceptors)
      (update ::http/interceptors update-interceptors components)))



(defn server
  "Starts the server and returns an initialized service map."
  [service-map]
  (http/start (http/create-server service-map)))



(defn stop-server
  "Stops the server."
  [initialized-service-map]
  (http/stop initialized-service-map))
