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


(defn wrap-route
  "Ring middleware for adding matched Reitit route to the server span."
  [handler]
  (fn
    ([{{:keys [template]} :reitit.core/match
       :as request}]
     (trace-http/add-route-data! template)
     (handler request))
    ([{:keys [io.opentelemetry/server-span-context]
       :as   request
       {:keys [template]} :reitit.core/match} respond raise]
     (trace-http/add-route-data! template {:context server-span-context})
     (handler request respond raise))))
