(ns example.common-utils.middleware
  (:require [steffan-westcott.clj-otel.api.trace.http :as trace-http]
            [steffan-westcott.clj-otel.api.trace.span :as span]))


(defn wrap-exception-event
  "Ring middleware for adding an exception event to the server span."
  [handler]
  (fn
    ([request]
     (try
       (handler request)
       (catch Throwable e
         (span/add-exception! e {:escaping? false})
         (throw e))))
    ([{:keys [io.opentelemetry/server-span-context]
       :as   request} respond raise]
     (try
       (handler request
                respond
                (fn [e]
                  (span/add-exception! e
                                       {:context   server-span-context
                                        :escaping? false})
                  (raise e)))
       (catch Throwable e
         (span/add-exception! e
                              {:context   server-span-context
                               :escaping? false})
         (raise e))))))



(defn wrap-reitit-route
  "Ring middleware to add matched Reitit route to the server span and Ring
  request map."
  [handler]
  (trace-http/wrap-route handler
                         (fn [request]
                           (get-in request [:reitit.core/match :template]))))