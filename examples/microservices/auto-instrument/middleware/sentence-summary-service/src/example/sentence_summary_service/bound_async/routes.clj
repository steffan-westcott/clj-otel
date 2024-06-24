(ns example.sentence-summary-service.bound-async.routes
  "HTTP routes, bound async implementation."
  (:require [example.common.core-async.utils :as async']
            [example.sentence-summary-service.bound-async.app :as app]
            [ring.util.response :as response]))


(defn- get-ping
  "Returns an empty response."
  [_ respond _]
  (respond (response/response nil)))



(defn- get-summary
  "Returns a response containing a summary of the given sentence."
  [components {{{:keys [sentence]} :query} :parameters} respond raise]
  (let [<summary (app/<build-summary components sentence)]
    (async'/ch->respond-raise <summary
                              (fn [summary]
                                (respond (response/response summary)))
                              raise)))



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
