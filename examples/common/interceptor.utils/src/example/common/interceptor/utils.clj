(ns example.common.interceptor.utils
  "Common interceptors for examples."
  (:require [clojure.data.json :as json]
            [io.pedestal.http :as http]
            [io.pedestal.http.content-negotiation :as content-negotiation]
            [ring.util.response :as response]))


(defn components-interceptor
  "Returns an interceptor which adds system components to the Pedestal context."
  [components]
  {:name  ::components
   :enter (fn [ctx]
            (assoc-in ctx [:request :components] components))})



(defn exception-response-interceptor
  "Returns an interceptor which converts an exception to an HTTP response."
  []
  {:name  ::exception-response
   :error (fn [ctx e]
            (let [exception (get (ex-data e) :exception e)
                  status    (:http.response/status (ex-data exception) 500)
                  resp      (-> (response/response {:message (ex-message exception)})
                                (response/status status))]
              (assoc ctx :response resp)))})



(defn content-negotiation-interceptor
  "Returns an interceptor that negotiates a supported response format."
  []
  (content-negotiation/negotiate-content ["application/json" "text/plain"]))



(defn- coerce-body
  [body content-type]
  (when (some? body)
    (case content-type
      "application/json" (json/write-str body)
      "text/plain"       (str body))))



(defn coerce-response-interceptor
  "Returns an interceptor that coerces the response to the format negotiated
   by `content-negotiation-interceptor`."
  []
  {:name  ::coerce-response
   :leave (fn [{:keys [request response]
                :as   ctx}]
            (let [content-type (get-in request [:accept :field] "application/json")
                  response     (-> response
                                   (update :headers assoc "Content-Type" content-type)
                                   (update :body coerce-body content-type))]
              (assoc ctx :response response)))})



(defn not-found-interceptor
  "Returns an interceptor that creates a 404 HTTP response."
  []
  {:name  ::not-found
   :leave (fn [{:keys [response]
                :as   ctx}]
            (if (http/response? response)
              ctx
              (assoc ctx
                     :response
                     (-> (response/response {:message "Not found"})
                         (response/status 404)))))})
