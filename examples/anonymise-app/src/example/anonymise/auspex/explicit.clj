(ns example.anonymise.auspex.explicit
  "CompletableFuture with auspex library, using explicit context"
  (:require [clojure.string :as str]
            [qbits.auspex :as aus]
            [steffan-westcott.clj-otel.api.trace.span :as span]
            [steffan-westcott.clj-otel.context :as context]))


(defn <replace-names
  "Returns a CompletableFuture containing string `s` with names replaced by `***`."
  [context s]
  (span/cf-span-binding [_ {:parent context
                            :name   "Replacing names"}]
    (aus/future
      (fn []
        (Thread/sleep 100)
        (str/replace s #"\b(alice|bob)\b" "***")))))


(defn <anonymise
  "Returns a CompletableFuture containing string `s` lowercased and anonymised."
  [context s]
  (span/cf-span-binding [context* {:parent context
                                   :name   "Anonymising string"}]
    (-> (aus/future
          (fn []
            (Thread/sleep 200)
            (str/lower-case s)))
        (aus/fmap #(<replace-names context* %)))))


(defn app
  [s]
  (span/with-span! "Running application"
    @(<anonymise (context/current) s)))


(comment

  ;; Exercise the application
  (app "Alice talks with Bob on the phone.")

  ;;
)
