(ns example.puzzle-service.async-chan-explicit.routes
  "HTTP routes, core.async implementation using explicit context."
  (:require [clojure.string :as str]
            [com.xadecimal.async-style :as style]
            [example.puzzle-service.async-chan-explicit.app :as app]
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
  (let [word-types (map keyword (str/split types #","))]
    (-> (app/<generate-puzzle components wrap-span-context word-types)
        (style/then #(response/response {:puzzle %}))
        (style/handle respond raise))))


(defn routes
  "Route data for all routes"
  [components]
  [["/ping" {:get get-ping}]
   ["/puzzle"
    {:get {:handler    (partial get-puzzle components)
           :parameters {:query [:map [:types :string]]}
           :responses  {200 {:body [:map [:puzzle :string]]}}}}]])
