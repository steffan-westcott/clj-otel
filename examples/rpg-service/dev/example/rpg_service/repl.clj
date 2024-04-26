(ns example.rpg-service.repl
  "Functions to operate and exercise the rpg-service system at the REPL."
  (:require [clojure.data.json :as json]
            [example.common.system :as common-system]
            [example.rpg-service.system :as system]
            [org.httpkit.client :as client]))


(defonce ^{:doc "Map of components in the running system."} system
  nil)

(defn start!
  "Starts the system."
  []
  (common-system/start! #'system system/with-system))

(defn stop!
  "Stops the system."
  []
  (common-system/stop! #'system))

(defn process-response
  "Returns the status and decoded JSON body of a response, or client error."
  [{:keys [status body error]}]
  (if error
    {:error error}
    {:status status
     :body   (json/read-str body
                            {:eof-error? false
                             :key-fn     keyword})}))

#_{:clj-kondo/ignore [:unresolved-var]}
(defn do-get-request
  "Make a GET request to the running system."
  [path]
  (process-response @(client/get (str "http://localhost:8080" path)
                                 {:headers {"Accept" "application/json"}
                                  :as      :text})))

(defn get-character
  "Request the running system for data on an RPG character."
  [id]
  (do-get-request (str "/character/" id)))

(defn get-inventory
  "Request the running system for the inventory carried by an RPG character."
  [id]
  (do-get-request (str "/character/" id "/inventory")))

(defn get-item
  "Request the running system for data on an item."
  [id]
  (do-get-request (str "/item/" id)))



(comment
  (start!)
  (get-character 1)
  (get-inventory 2)
  (get-item 4)
  (stop!)
  ;
)
