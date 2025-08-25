(ns example.puzzle-service.async-chan-bound.routes
  "HTTP routes, core.async implementation using bound context."
  (:require [clojure.string :as str]
            [com.xadecimal.async-style :as style]
            [example.common.async.async-style :as style']
            [example.puzzle-service.async-chan-bound.app :as app]
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
        (style/then #(response/response {:puzzle %}))
        (style'/ch->respond-raise respond raise))))


(defn routes
  "Route data for all routes"
  [components]
  [["/ping" {:get get-ping}]
   ["/puzzle"
    {:get {:handler    (partial get-puzzle components)
           :parameters {:query [:map [:types :string]]}
           :responses  {200 {:body [:map [:puzzle :string]]}}}}]])
