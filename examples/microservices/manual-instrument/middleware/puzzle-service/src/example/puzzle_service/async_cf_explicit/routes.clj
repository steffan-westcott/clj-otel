(ns example.puzzle-service.async-cf-explicit.routes
  "HTTP routes, async CompletableFuture implementation using explicit context."
  (:require [clojure.string :as str]
            [example.common.async.auspex :as aus']
            [example.puzzle-service.async-cf-explicit.app :as app]
            [qbits.auspex :as aus]
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
        (aus/then #(response/response {:puzzle %}))
        (aus'/cf->respond-raise respond raise))))


(defn routes
  "Route data for all routes"
  [components]
  [["/ping" {:get get-ping}]
   ["/puzzle"
    {:get {:handler    (partial get-puzzle components)
           :parameters {:query [:map [:types :string]]}
           :responses  {200 {:body [:map [:puzzle :string]]}}}}]])

