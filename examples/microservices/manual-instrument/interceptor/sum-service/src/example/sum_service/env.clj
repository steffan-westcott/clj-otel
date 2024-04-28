(ns example.sum-service.env
  "Functions for reading application configuration."
  (:require [aero.core :as aero]
            [clojure.java.io :as io]))

(defonce ^{:doc "Application configuration"} config
  nil)


(defn read-config
  "Reads configuration map from a file."
  []
  (aero/read-config (io/resource "config.edn")))



(defn set-config!
  "Sets var `config` to config map read from a file."
  []
  (alter-var-root #'config (constantly (read-config))))
