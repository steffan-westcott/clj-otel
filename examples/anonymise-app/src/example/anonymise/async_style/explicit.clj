(ns example.anonymise.async-style.explicit
  "core.async with async-style library, using explicit context"
  (:require [clojure.string :as str]
            [com.xadecimal.async-style :as style]
            [steffan-westcott.clj-otel.api.trace.chan-span :as chan-span]
            [steffan-westcott.clj-otel.api.trace.span :as span]))


(defn <replace-names
  "Returns a channel containing string `s` with names replaced by `***`."
  [context s]
  (chan-span/chan-span-binding [_ {:parent context
                                   :name   "Replacing names"}]
    (style/async
      (Thread/sleep 100)
      (str/replace s #"\b(alice|bob)\b" "***"))))


(defn <anonymise
  "Returns a channel containing string `s` lowercased and anonymised."
  [context s]
  (chan-span/chan-span-binding [context* {:parent context
                                          :name   "Anonymising string"}]
    (-> (style/async
          (Thread/sleep 200)
          (str/lower-case s))
        (style/then #(<replace-names context* %)))))


(defn app
  [s]
  (span/with-span-binding [context "Running application"]
    (style/wait (<anonymise context s))))


(comment

  ;; Exercise the application
  (app "Alice talks with Bob on the phone.")

  ;;
)
