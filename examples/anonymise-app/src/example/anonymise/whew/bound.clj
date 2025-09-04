(ns example.anonymise.whew.bound
  "CompletableFuture with whew library, using bound context"
  (:require [clojure.string :as str]
            [whew.core :as whew]
            [steffan-westcott.clj-otel.api.trace.span :as span]))


(defn <replace-names
  "Returns a CompletableFuture containing string `s` with names replaced by `***`."
  [s]
  (span/async-bound-cf-span "Replacing names"
                            (whew/future
                              (Thread/sleep 100)
                              (str/replace s #"\b(alice|bob)\b" "***"))))


(defn <anonymise
  "Returns a CompletableFuture containing string `s` lowercased and anonymised."
  [s]
  (span/async-bound-cf-span "Anonymising string"
                            (-> (whew/future
                                  (Thread/sleep 200)
                                  (str/lower-case s))
                                (whew/then-fn (bound-fn* <replace-names)))))


(defn app
  [s]
  (span/with-bound-span! "Running application"
    (whew/deref (<anonymise s))))


(comment

  ;; Exercise the application
  (app "Alice talks with Bob on the phone.")

  ;;
)
