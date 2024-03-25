(ns example.random-word-service.routes
  "HTTP routes."
  (:require [example.random-word-service.app :as app]
            [ring.util.response :as response]))


(defn- get-ping
  "Returns an empty response."
  [_]
  (response/response nil))



(defn get-random-word
  "Returns a response containing a random word of the requested type."
  [components {{{:keys [type]} :query} :parameters}]
  (let [word (app/random-word components (keyword type))]
    (Thread/sleep ^long (+ 20 (rand-int 20)))
    (response/response word)))



(defn routes
  "Route data for all routes."
  [components]
  [["/ping" {:get get-ping}]
   ["/random-word"
    {:get {:handler    (partial get-random-word components)
           :parameters {:query [:map [:type :string]]}
           :responses  {200 {:body [:string]}}}}]])
