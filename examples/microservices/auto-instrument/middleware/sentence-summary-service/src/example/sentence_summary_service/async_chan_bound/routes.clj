(ns example.sentence-summary-service.async-chan-bound.routes
  "HTTP routes, core.async implementation using bound context."
  (:require [com.xadecimal.async-style :as style]
            [example.common.async.async-style :as style']
            [example.sentence-summary-service.async-chan-bound.app :as app]
            [ring.util.response :as response]))


(defn- get-ping
  "Returns an empty response."
  [_ respond _]
  (respond (response/response nil)))



(defn- get-summary
  "Returns a response containing a summary of the given sentence."
  [components {{{:keys [sentence]} :query} :parameters} respond raise]
  (-> (app/<build-summary components sentence)
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
