(ns example.anonymise.promesa.bound
  "CompletableFuture with promesa library, using bound context"
  (:require [clojure.string :as str]
            [promesa.core :as prom]
            [steffan-westcott.clj-otel.api.trace.span :as span]))


(defn <replace-names
  "Returns a CompletableFuture containing string `s` with names replaced by `***`."
  [s]
  (span/async-bound-cf-span "Replacing names"
                            (prom/future
                              (Thread/sleep 100)
                              (span/add-event! "Nearly done")
                              (str/replace s #"\b(alice|bob)\b" "***"))))


(defn <anonymise
  "Returns a CompletableFuture containing string `s` lowercased and anonymised."
  [s]
  (span/async-bound-cf-span "Anonymising string"
                            (->> (prom/future
                                   (Thread/sleep 200)
                                   (str/lower-case s))
                                 (prom/mcat (bound-fn* <replace-names)))))


(defn app
  [s]
  (span/with-bound-span! "Running application"
    @(<anonymise s)))


(comment

  ;; Exercise the application
  (app "Alice talks with Bob on the phone.")

  ;;
)
