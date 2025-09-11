(ns example.anonymise.missionary.explicit
  "Missionary library, using explicit context"
  (:require [clojure.string :as str]
            [missionary.core :as m]
            [steffan-westcott.clj-otel.api.trace.span :as span]
            [steffan-westcott.clj-otel.api.trace.task-span :as task-span]
            [steffan-westcott.clj-otel.context :as context]))


(defn <replace-names
  "Returns a task for string `s` with names replaced by `***`."
  [context s]
  (task-span/task-span-binding [context* {:parent context
                                          :name   "Replacing names"}]
    (m/sp
      (Thread/sleep 100)
      (span/add-span-data! {:context context*
                            :event   {:name "Nearly done"}})
      (str/replace s #"\b(alice|bob)\b" "***"))))


(defn <anonymise
  "Returns a task for string `s` lowercased and anonymised."
  [context s]
  (task-span/task-span-binding [context* {:parent context
                                          :name   "Anonymising string"}]
    (m/sp
      (Thread/sleep 200)
      (m/? (<replace-names context* (str/lower-case s))))))


(defn app
  [s]
  (span/with-span! "Running application"
    (m/? (<anonymise (context/current) s))))


(comment

  ;; Exercise the application
  (app "Alice talks with Bob on the phone.")

  ;;
)
