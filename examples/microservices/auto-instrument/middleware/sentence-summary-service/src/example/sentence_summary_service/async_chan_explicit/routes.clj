(ns example.sentence-summary-service.async-chan-explicit.routes
  "HTTP routes, core.async implementation using explicit context."
  (:require [com.xadecimal.async-style :as style]
            [example.common.async.async-style :as style']
            [example.sentence-summary-service.async-chan-explicit.app :as app]
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
  (-> (app/<build-summary components wrap-span-context sentence)
      (style/then #(response/response %))
      (style'/ch->respond-raise respond raise)))



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
