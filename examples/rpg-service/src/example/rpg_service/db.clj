(ns example.rpg-service.db
  "Database components"
  (:require [hikari-cp.core :as hikari]
            [honeyeql.db :as heql-db]))


(defn datasource
  "Returns a pooled datasource."
  [opts]
  (-> opts
      (assoc :auto-commit true :read-only false)
      hikari/make-datasource))



(defn stop-datasource
  "Stops the given pooled `datasource`."
  [datasource]
  (hikari/close-datasource datasource))



(defn eql-db
  "Returns a database adapter for the given `datasource` that accepts EQL
   queries."
  [datasource]
  (heql-db/initialize datasource))
