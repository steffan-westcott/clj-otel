(ns example.puzzle-service.bound-async.routes
  "HTTP routes, bound async implementation."
  (:require [clojure.string :as str]
            [example.common.core-async.utils :as async']
            [example.puzzle-service.bound-async.app :as app]
            [ring.util.response :as response]))


(defn get-ping
  "Ring handler for ping health check."
  [_ respond _]
  (respond (response/response nil)))



(defn get-puzzle
  "Returns a response containing a puzzle of the requested word types."
  [components {{{:keys [types]} :query} :parameters} respond raise]
  (let [word-types (map keyword (str/split types #","))
        <puzzle    (app/<generate-puzzle components word-types)]
    (async'/ch->respond-raise <puzzle
                              (fn [puzzle]
                                (respond (response/response {:puzzle puzzle})))
                              raise)))


(defn routes
  "Route data for all routes"
  [components]
  [["/ping" {:get get-ping}]
   ["/puzzle"
    {:get {:handler    (partial get-puzzle components)
           :parameters {:query [:map [:types :string]]}
           :responses  {200 {:body [:map [:puzzle :string]]}}}}]])
