(ns example.sentence-summary-service
  (:require [example.sentence-summary-service-bound-async :as bound-async]
            [example.sentence-summary-service-explicit-async :as explicit-async]
            [example.sentence-summary-service-sync :as sync])
  (:gen-class))

(defn -main
  "Starts a sentence-summary-service server instance according to environment variable `SERVER`."
  []
  (case (System/getenv "SERVER")

    ;; Example of asynchronous server using bound context
    "bound-async"    (bound-async/server)

    ;; Example of asynchronous server using explicit context
    "explicit-async" (explicit-async/server)

    ;; Example of synchronous server
    (sync/server)))