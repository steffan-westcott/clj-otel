(ns example.solar-system-service.logging
  "Management of application logging"
  (:require [steffan-westcott.clj-otel.adapter.log4j :as log4j]))


(defn initialize
  "Initializes Log4j appender."
  []
  (log4j/initialize))
