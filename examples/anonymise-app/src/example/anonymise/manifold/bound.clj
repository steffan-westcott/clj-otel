(ns example.anonymise.manifold.bound
  "Manifold library, using bound context"
  (:require [clojure.string :as str]
            [manifold.deferred :as d]
            [steffan-westcott.clj-otel.api.trace.d-span :as d-span]
            [steffan-westcott.clj-otel.api.trace.span :as span]))


(defn <replace-names
  "Returns a deferred containing string `s` with names replaced by `***`."
  [s]
  (d-span/async-bound-d-span "Replacing names"
                             (d/future
                               (Thread/sleep 100)
                               (span/add-event! "Nearly done")
                               (str/replace s #"\b(alice|bob)\b" "***"))))


(defn <anonymise
  "Returns a deferred containing string `s` lowercased and anonymised."
  [s]
  (d-span/async-bound-d-span "Anonymising string"
                             (-> (d/future
                                   (Thread/sleep 200)
                                   (str/lower-case s))
                                 (d/chain' (bound-fn* <replace-names)))))


(defn app
  [s]
  (span/with-bound-span! "Running application"
    @(<anonymise s)))


(comment

  ;; Exercise the application
  (app "Alice talks with Bob on the phone.")

  ;;
)
