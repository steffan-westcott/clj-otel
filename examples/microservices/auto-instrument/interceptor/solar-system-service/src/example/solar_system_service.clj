(ns example.solar-system-service
  (:require [example.solar-system-service-bound-async :as bound-async]
            [example.solar-system-service-explicit-async :as explicit-async]
            [example.solar-system-service-sync :as sync])
  (:gen-class))

(defn -main
  "Starts a solar-system-service server instance according to environment variable `SERVER`."
  []
  (let [conf {:endpoints {:planet-service (System/getenv "PLANET_SERVICE_ENDPOINT")}}]
    (case (System/getenv "SERVER")

      ;; Example of asynchronous server using bound context
      "bound-async"    (bound-async/server conf)

      ;; Example of asynchronous server using explicit context
      "explicit-async" (explicit-async/server conf)

      ;; Example of synchronous server
      (sync/server conf))))
