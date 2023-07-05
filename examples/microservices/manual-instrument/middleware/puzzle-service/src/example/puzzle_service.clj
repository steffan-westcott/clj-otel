(ns example.puzzle-service
  (:require [example.puzzle-service-bound-async :as bound-async]
            [example.puzzle-service-explicit-async :as explicit-async]
            [example.puzzle-service-sync :as sync])
  (:gen-class))

(defn -main
  "Starts a puzzle-service server instance according to selector."
  ([]
   (-main nil))
  ([selector]
   (case selector

     ;; Example of asynchronous server using bound context
     "bound-async"    (bound-async/server)

     ;; Example of asynchronous server using explicit context
     "explicit-async" (explicit-async/server)

     ;; Example of synchronous server
     (sync/server))))
