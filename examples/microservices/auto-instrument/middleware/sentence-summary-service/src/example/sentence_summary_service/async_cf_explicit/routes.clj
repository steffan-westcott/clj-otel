(ns example.sentence-summary-service.async-cf-explicit.routes
  "HTTP routes, async CompletableFuture implementation using explicit context."
  (:require [example.common.async.auspex :as aus']
            [example.sentence-summary-service.async-cf-explicit.app :as app]
            [qbits.auspex :as aus]
            [ring.util.response :as response]
            [steffan-westcott.clj-otel.api.trace.span :as span]))


(defn- get-ping
  "Returns an empty response."
  [_ respond _]
  (respond (response/response nil)))



(defn- get-summary
  "Returns a response containing a summary of the given sentence."
  [components
   {::span/keys [wrap-span-context]
    {{:keys [sentence]} :query} :parameters} respond raise]
  (-> (app/<build-summary components wrap-span-context sentence)
      (aus/then #(response/response %))
      (aus'/cf->respond-raise respond raise)))



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
