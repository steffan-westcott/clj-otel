(ns example.common.async.response
  "Common utilities for HTTP responses for use in async examples."
  (:require [ring.util.response :as response]))


(defn exception-response
  "Converts exception to a response, with status set to `:http.response/status`
   value if exception is an `IExceptionInfo` instance, 500 Server Error
   otherwise."
  [e]
  (-> (response/response {:message (ex-message e)})
      (response/status (:http.response/status (ex-data e) 500))))
