(ns ^:no-doc steffan-westcott.clj-otel.config
  "Configuration of clj-otel library."
  (:require [clojure.java.io :as io]))

(def config
  "Configuration map of the clj-otel library."
  (-> "steffan_westcott/clj_otel/config.edn"
      io/resource
      slurp
      read-string))
