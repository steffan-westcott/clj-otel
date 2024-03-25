(ns example.sentence-summary-service.env
  "Environment components, used to configure other system components."
  (:require [aero.core :as aero]
            [clojure.java.io :as io]))


(defn config
  "Returns configuration."
  []
  (aero/read-config (io/resource "config.edn")))
