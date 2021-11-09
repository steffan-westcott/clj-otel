(ns ^:no-doc steffan-westcott.otel.config
  "Configuration of clj-otel library."
  (:require [clojure.java.io :as io]))

(def config
  "Configuration map of the clj-otel library."
  (-> "steffan_westcott/otel/config.edn" io/resource slurp read-string))
