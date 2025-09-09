(ns example.anonymise.promesa.explicit
  "CompletableFuture with promesa library, using explicit context"
  (:require [clojure.string :as str]
            [promesa.core :as prom]
            [steffan-westcott.clj-otel.api.trace.span :as span]
            [steffan-westcott.clj-otel.context :as context]))


(defn <replace-names
  "Returns a CompletableFuture containing string `s` with names replaced by `***`."
  [context s]
  (span/cf-span-binding [context* {:parent context
                                   :name   "Replacing names"}]
    (prom/future
      (Thread/sleep 100)
      (span/add-span-data! {:context context*
                            :event   {:name "Nearly done"}})
      (str/replace s #"\b(alice|bob)\b" "***"))))


(defn <anonymise
  "Returns a CompletableFuture containing string `s` lowercased and anonymised."
  [context s]
  (span/cf-span-binding [context* {:parent context
                                   :name   "Anonymising string"}]
    (->> (prom/future
           (Thread/sleep 200)
           (str/lower-case s))
         (prom/mcat #(<replace-names context* %)))))


(defn app
  [s]
  (span/with-span! "Running application"
    @(<anonymise (context/current) s)))


(comment

  ;; Exercise the application
  (app "Alice talks with Bob on the phone.")

  ;;
)

