(ns example.common-utils.interceptor
  (:require [ring.util.response :as response]
            [steffan-westcott.clj-otel.api.trace.span :as span]))


(defn- exception-response
  "Converts exception to a response, with status set to `:status` value if
  exception is an `IExceptionInfo` instance, 500 Server Error otherwise."
  [e]
  (let [resp   (response/response (ex-message e))
        status (:status (ex-data e) 500)]
    (response/status resp status)))



(defn exception-response-interceptor
  "Returns an interceptor which converts a synchronously thrown exception to a
  response."
  []
  {:name  ::exception-response
   :error (fn [{:keys [io.opentelemetry/server-span-context]
                :as   ctx} e]
            (span/add-interceptor-exception! e
                                             {:context   server-span-context
                                              :escaping? false})
            (let [exception (get (ex-data e) :exception e)]
              (assoc ctx :response (exception-response exception))))})