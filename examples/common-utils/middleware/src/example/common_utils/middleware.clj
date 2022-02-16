(ns example.common-utils.middleware
  (:require [ring.util.response :as response]
            [steffan-westcott.clj-otel.api.trace.span :as span]))


(defn- exception-response
  [e]
  (let [resp   (response/response (ex-message e))
        status (:status (ex-data e) 500)]
    (response/status resp status)))



(defn wrap-exception
  "Ring middleware for wrapping an exception as a response."
  [handler]
  (fn
    ([request]
     (try
       (handler request)
       (catch Throwable e
         (span/add-exception! e {:escaping? false})
         (exception-response e))))
    ([{:keys [io.opentelemetry/server-span-context]
       :as   request} respond _]
     (try
       (handler request
                respond
                (fn [e]
                  (span/add-exception! e
                                       {:context   server-span-context
                                        :escaping? false})
                  (respond (exception-response e))))
       (catch Throwable e
         (span/add-exception! e
                              {:context   server-span-context
                               :escaping? false})
         (respond (exception-response e)))))))
