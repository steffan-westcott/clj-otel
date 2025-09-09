(ns example.puzzle-service.async-d-bound.routes
  "HTTP routes, Manifold implementation using bound context."
  (:require [clojure.string :as str]
            [example.puzzle-service.async-d-bound.app :as app]
            [manifold.deferred :as d]
            [ring.util.response :as response]))


(defn get-ping
  "Ring handler for ping health check."
  [_ respond _]
  (respond (response/response nil)))



(defn get-puzzle
  "Returns a response containing a puzzle of the requested word types."
  [components {{{:keys [types]} :query} :parameters} respond raise]
  (let [word-types (map keyword (str/split types #","))]
    (-> (app/<generate-puzzle components word-types)
        (d/chain' #(response/response {:puzzle %}))
        (d/on-realized respond raise))))


(defn routes
  "Route data for all routes"
  [components]
  [["/ping" {:get get-ping}]
   ["/puzzle"
    {:get {:handler    (partial get-puzzle components)
           :parameters {:query [:map [:types :string]]}
           :responses  {200 {:body [:map [:puzzle :string]]}}}}]])
