(ns example.anonymise.async-style.bound
  "core.async with async-style library, using bound context"
  (:require [clojure.string :as str]
            [com.xadecimal.async-style :as style]
            [steffan-westcott.clj-otel.api.trace.chan-span :as chan-span]
            [steffan-westcott.clj-otel.api.trace.span :as span]))


(defn <replace-names
  "Returns a channel containing string `s` with names replaced by `***`."
  [s]
  (chan-span/async-bound-chan-span "Replacing names"
    (style/async
      (Thread/sleep 100)
      (span/add-event! "Nearly done")
      (str/replace s #"\b(alice|bob)\b" "***"))))


(defn <anonymise
  "Returns a channel containing string `s` lowercased and anonymised."
  [s]
  (chan-span/async-bound-chan-span "Anonymising string"
    (-> (style/async
          (Thread/sleep 200)
          (str/lower-case s))
        (style/then <replace-names))))


(defn app
  [s]
  (span/with-bound-span! "Running application"
    (style/wait (<anonymise s))))


(comment

  ;; Exercise the application
  (app "Alice talks with Bob on the phone.")

  ;;
)
