(ns example.average-service
  (:require [example.average-service-bound-async :as bound-async]
            [example.average-service-explicit-async :as explicit-async]
            [example.average-service-sync :as sync])
  (:gen-class))


(defn -main
  "Starts an average-service server instance according to selector."
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