(ns example.puzzle-service.async-task-explicit.routes
  "HTTP routes, Missionary implementation using explicit context."
  (:require [clojure.string :as str]
            [example.puzzle-service.async-task-explicit.app :as app]
            [missionary.core :as m]
            [ring.util.response :as response]
            [steffan-westcott.clj-otel.api.trace.span :as span]))


(defn get-ping
  "Ring handler for ping health check."
  [_ respond _]
  (respond (response/response nil)))



(defn get-puzzle
  "Returns a response containing a puzzle of the requested word types."
  [components
   {::span/keys [wrap-span-context]
    {{:keys [types]} :query} :parameters} respond raise]
  ((m/sp
     (let [word-types (map keyword (str/split types #","))
           puzzle     (m/? (app/<generate-puzzle components wrap-span-context word-types))]
       (response/response {:puzzle puzzle})))
   respond
   raise))


(defn routes
  "Route data for all routes"
  [components]
  [["/ping" {:get get-ping}]
   ["/puzzle"
    {:get {:handler    (partial get-puzzle components)
           :parameters {:query [:map [:types :string]]}
           :responses  {200 {:body [:map [:puzzle :string]]}}}}]])
