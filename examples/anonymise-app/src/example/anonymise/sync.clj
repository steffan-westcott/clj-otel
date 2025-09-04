(ns example.anonymise.sync
  "Synchronous"
  (:require [clojure.string :as str]
            [steffan-westcott.clj-otel.api.trace.span :as span]))

(defn replace-names
  [s]
  (span/with-span! "Replacing names"
    (Thread/sleep 100)
    (str/replace s #"\b(alice|bob)\b" "***")))


(defn anonymise
  [s]
  (span/with-span! "Anonymising string"
    (-> (do
          (Thread/sleep 200)
          (str/lower-case s))
        replace-names)))


(defn app
  [s]
  (span/with-span! "Running application"
    (anonymise s)))


(comment

  ;; Exercise the application
  (app "Alice talks with Bob on the phone.")

  ;;
)
