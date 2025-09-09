(ns example.anonymise.manifold.explicit
  "Manifold library, using explicit context"
  (:require [clojure.string :as str]
            [manifold.deferred :as d]
            [steffan-westcott.clj-otel.api.trace.d-span :as d-span]
            [steffan-westcott.clj-otel.api.trace.span :as span]
            [steffan-westcott.clj-otel.context :as context]))


(defn <replace-names
  "Returns a deferred containing string `s` with names replaced by `***`."
  [context s]
  (d-span/d-span-binding [context*
                          {:parent context
                           :name   "Replacing names"}]
                         (d/future
                           (Thread/sleep 100)
                           (span/add-span-data! {:context context*
                                                 :event   {:name "Nearly done"}})
                           (str/replace s #"\b(alice|bob)\b" "***"))))


(defn <anonymise
  "Returns a deferred containing string `s` lowercased and anonymised."
  [context s]
  (d-span/d-span-binding [context*
                          {:parent context
                           :name   "Anonymising string"}]
                         (-> (d/future
                               (Thread/sleep 200)
                               (str/lower-case s))
                             (d/chain #(<replace-names context* %)))))


(defn app
  [s]
  (span/with-span! "Running application"
    @(<anonymise (context/current) s)))


(comment

  ;; Exercise the application
  (app "Alice talks with Bob on the phone.")

  ;;
)
