(ns example.sentence-summary-service.async-task-explicit.routes
  "HTTP routes, Missionary implementation using explicit context."
  (:require [example.sentence-summary-service.async-task-explicit.app :as app]
            [missionary.core :as m]
            [ring.util.response :as response]
            [steffan-westcott.clj-otel.api.trace.span :as span]))


(defn get-ping
  "Returns an empty response."
  [_ respond _]
  (respond (response/response nil)))



(defn get-summary
  "Returns a response containing a summary of the given sentence."
  [components
   {::span/keys [wrap-span-context]
    {{:keys [sentence]} :query} :parameters} respond raise]
  ((m/sp
     (let [summary (m/? (app/<build-summary components wrap-span-context sentence))]
       (response/response summary)))
   respond
   raise))



(defn routes
  "Route data for all routes"
  [components]
  [["/ping" {:get get-ping}]
   ["/summary"
    {:get {:handler    (partial get-summary components)
           :parameters {:query [:map [:sentence :string]]}
           :responses  {200 {:body [:map ;
                                    [:words :int] ;
                                    [:shortest-length :int] ;
                                    [:longest-length :int]]}}}}]])

