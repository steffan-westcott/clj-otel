(ns example.puzzle-service.sync.routes
  "HTTP routes, synchronous implementation."
  (:require [clojure.string :as str]
            [example.puzzle-service.sync.app :as app]
            [ring.util.response :as response]))


(defn get-ping
  "Returns an empty response."
  [_]
  (response/response nil))



(defn get-puzzle
  "Returns a response containing a puzzle of the requested word types."
  [components {{{:keys [types]} :query} :parameters}]
  (let [word-types (map keyword (str/split types #","))]
    (response/response (app/generate-puzzle components word-types))))



(defn routes
  "Route data for all routes"
  [components]
  [["/ping" {:get get-ping}]
   ["/puzzle"
    {:get {:handler    (partial get-puzzle components)
           :parameters {:query [:map [:types :string]]}
           :responses  {200 {:body [:string]}}}}]])
