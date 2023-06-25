(ns example.common.interceptor.utils
  (:require [ring.util.response :as response]))


(defn exception-response-interceptor
  "Returns an interceptor which converts a synchronously thrown exception to an
   HTTP response."
  []
  {:name  ::exception-response
   :error (fn [ctx e]
            (let [exception (get (ex-data e) :exception e)
                  resp      (response/response (ex-message exception))
                  status    (:http.response/status (ex-data exception) 500)]
              (assoc ctx :response (response/status resp status))))})
