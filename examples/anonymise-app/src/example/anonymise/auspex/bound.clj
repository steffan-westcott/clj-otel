(ns example.anonymise.auspex.bound
  "CompletableFuture with auspex library, using bound context"
  (:require [clojure.string :as str]
            [qbits.auspex :as aus]
            [steffan-westcott.clj-otel.api.trace.span :as span]))


(defn <replace-names
  "Returns a CompletableFuture containing string `s` with names replaced by `***`."
  [s]
  (span/async-bound-cf-span "Replacing names"
                            (aus/future
                              (bound-fn []
                                (Thread/sleep 100)
                                (span/add-event! "Nearly done")
                                (str/replace s #"\b(alice|bob)\b" "***")))))


(defn <anonymise
  "Returns a CompletableFuture containing string `s` lowercased and anonymised."
  [s]
  (span/async-bound-cf-span "Anonymising string"
                            (-> (aus/future
                                  (fn []
                                    (Thread/sleep 200)
                                    (str/lower-case s)))
                                (aus/fmap (bound-fn* <replace-names)))))


(defn app
  [s]
  (span/with-bound-span! "Running application"
    @(<anonymise s)))


(comment

  ;; Exercise the application
  (app "Alice talks with Bob on the phone.")

  ;;
)
