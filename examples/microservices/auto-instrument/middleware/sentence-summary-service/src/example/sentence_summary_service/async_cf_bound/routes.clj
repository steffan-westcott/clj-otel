(ns example.sentence-summary-service.async-cf-bound.routes
  "HTTP routes, async CompletableFuture implementation using bound context."
  (:require [example.common.async.auspex :as aus']
            [example.sentence-summary-service.async-cf-bound.app :as app]
            [qbits.auspex :as aus]
            [ring.util.response :as response]))


(defn- get-ping
  "Returns an empty response."
  [_ respond _]
  (respond (response/response nil)))



(defn- get-summary
  "Returns a response containing a summary of the given sentence."
  [components {{{:keys [sentence]} :query} :parameters} respond raise]
  (-> (app/<build-summary components sentence)
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

