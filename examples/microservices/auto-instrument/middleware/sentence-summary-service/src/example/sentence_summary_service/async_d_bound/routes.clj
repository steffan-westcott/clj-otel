(ns example.sentence-summary-service.async-d-bound.routes
  "HTTP routes, Manifold implementation using bound context."
  (:require [example.sentence-summary-service.async-d-bound.app :as app]
            [manifold.deferred :as d]
            [ring.util.response :as response]))


(defn- get-ping
  "Returns an empty response."
  [_ respond _]
  (respond (response/response nil)))



(defn- get-summary
  "Returns a response containing a summary of the given sentence."
  [components {{{:keys [sentence]} :query} :parameters} respond raise]
  (-> (app/<build-summary components sentence)
      (d/chain' #(response/response %))
      (d/on-realized respond raise)))



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
