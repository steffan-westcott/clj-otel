(ns example.sum-service.server
  "HTTP server components."
  (:require [example.common.async.interceptor :as common-interceptor]
            [example.sum-service.env :refer [config]]
            [example.sum-service.routes :as routes]
            [io.pedestal.http :as http]
            [io.pedestal.interceptor :as interceptor]
            [steffan-westcott.clj-otel.api.metrics.http.server :as metrics-http-server]
            [steffan-westcott.clj-otel.api.trace.http :as trace-http]))


(defn- update-interceptors
  "Returns modified default interceptors."
  [default-interceptors components]
  (map interceptor/interceptor
       (concat (;; As this application is not run with the OpenTelemetry instrumentation
                ;; agent, create a server span for each request. Because all request
                ;; processing for this service is synchronous, the current context is set
                ;; for each request.
                trace-http/server-span-interceptors {:create-span? true})

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
  (-> {::http/routes  #(routes/routes) ; on every request, look up routes/routes var and
                                       ; rebuild
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
