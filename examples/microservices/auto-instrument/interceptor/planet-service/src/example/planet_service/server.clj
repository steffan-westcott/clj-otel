(ns example.planet-service.server
  "HTTP server components."
  (:require [example.common.interceptor.utils :as interceptor-utils]
            [example.planet-service.env :refer [config]]
            [example.planet-service.routes :as routes]
            [io.pedestal.http :as http]
            [io.pedestal.interceptor :as interceptor]
            [steffan-westcott.clj-otel.api.trace.http :as trace-http]))


(defn- update-interceptors
  "Returns modified default interceptors."
  [default-interceptors components]
  (map interceptor/interceptor
       (concat (;; As this application is run with the OpenTelemetry instrumentation agent,
                ;; a server span will be provided by the agent and there is no need to
                ;; create another one.
                trace-http/server-span-interceptors {:create-span? false})

               ;; Negotiate content formats
               [(interceptor-utils/content-negotiation-interceptor)]

               ;; Coerce HTTP response format
               [(interceptor-utils/coerce-response-interceptor)]

               ;; Default Pedestal interceptor stack
               default-interceptors

               ;; Adds matched route data to server spans
               [(trace-http/route-interceptor)]

               ;; Convert exception to HTTP response
               [(interceptor-utils/exception-response-interceptor)]

               ;; Add exception event to server span
               [(trace-http/exception-event-interceptor)]

               ;; Add system components to context
               [(interceptor-utils/components-interceptor components)])))



(defn service-map
  "Returns a service map ready for creating an HTTP server."
  [components]
  (-> {::http/routes #(routes/routes) ; on every request, look up routes/routes var and rebuild
       ::http/type   :jetty
       ::http/host   "0.0.0.0"
       ::http/join?  false
       ::http/not-found-interceptor (interceptor-utils/not-found-interceptor)}
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
