(ns example.anonymise.whew.explicit
  "CompletableFuture with whew library, using explicit context"
  (:require [clojure.string :as str]
            [steffan-westcott.clj-otel.api.trace.span :as span]
            [steffan-westcott.clj-otel.context :as context]
            [whew.core :as whew]))


(defn <replace-names
  "Returns a CompletableFuture containing string `s` with names replaced by `***`."
  [context s]
  (span/cf-span-binding [_ {:parent context
                            :name   "Replacing names"}]
    (whew/future
      (Thread/sleep 100)
      (str/replace s #"\b(alice|bob)\b" "***"))))


(defn <anonymise
  "Returns a CompletableFuture containing string `s` lowercased and anonymised."
  [context s]
  (span/cf-span-binding [context* {:parent context
                                   :name   "Anonymising string"}]
    (-> (whew/future
          (Thread/sleep 200)
          (str/lower-case s))
        (whew/then-fn #(<replace-names context* %)))))


(defn app
  "Example application using whew with explicit context."
  [s]
  (span/with-span! "Running application"
    (whew/deref (<anonymise (context/current) s))))


(comment

  ;; Exercise the application
  (app "Alice talks with Bob on the phone.")

  ;;
)
