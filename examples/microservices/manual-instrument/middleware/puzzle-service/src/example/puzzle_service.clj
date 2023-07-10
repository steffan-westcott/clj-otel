(ns example.puzzle-service
  (:require [example.puzzle-service-bound-async :as bound-async]
            [example.puzzle-service-explicit-async :as explicit-async]
            [example.puzzle-service-sync :as sync])
  (:gen-class))

(defn -main
  "Starts a puzzle-service server instance according to environment variable `SERVER`."
  []
  (let [conf {:endpoints {:random-word-service (System/getenv "RANDOM_WORD_SERVICE_ENDPOINT")}}]
    (case (System/getenv "SERVER")

      ;; Example of asynchronous server using bound context
      "bound-async"    (bound-async/server conf)

      ;; Example of asynchronous server using explicit context
      "explicit-async" (explicit-async/server conf)

      ;; Example of synchronous server
      (sync/server conf))))
