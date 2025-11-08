(ns example.puzzle-service.async-task-explicit.app
  "Application logic, Missionary implementation using explicit context."
  (:require [clojure.string :as str]
            [example.common.log4j2.utils :as log]
            [example.puzzle-service.async-task-explicit.requests :as requests]
            [missionary.core :as m]
            [steffan-westcott.clj-otel.api.metrics.instrument :as instrument]
            [steffan-westcott.clj-otel.api.trace.span :as span]
            [steffan-westcott.clj-otel.api.trace.task-span :as task-span]))


(defn- <scramble
  "Given a word, returns a task of a string of the scrambled word."
  [context word]
  (m/via m/cpu

    ;; Wrap synchronous function body with an internal span. Context containing
    ;; internal span is assigned to `context*`.
    (span/with-span-binding [context* {:parent     context
                                       :name       "Scrambling word"
                                       :attributes {:system/word word}}]

      (Thread/sleep 5) ; pretend to be CPU intensive
      (log/debug context* "About to scramble word:" word)
      (let [scrambled-word (->> word
                                seq
                                shuffle
                                (apply str))]
        (log/debug context* "Scrambled word:" scrambled-word)

        ;; Add more attributes to internal span
        (span/add-span-data! {:context    context*
                              :attributes {:service.puzzle/scrambled-word scrambled-word}})

        scrambled-word))))



(defn- <scrambled-random-words
  "Gets random words of the requested word types, scrambles them and returns
   a task of a vector containing the scrambled words."
  [components context word-types]

  ;; Wrap task with an asynchronous internal span. Context containing
  ;; internal span is assigned to `context*`.
  (task-span/task-span-binding [context* {:name       "Getting scrambled random words"
                                          :attributes {:system/word-types word-types}
                                          :parent     context}]

    (apply m/join
           vector
           (map (fn [word-type]
                  (m/sp
                    (let [word (m/? (requests/<get-random-word components context* word-type))]
                      (m/? (<scramble context* word)))))
                word-types))))


(defn <generate-puzzle
  "Constructs a puzzle string containing scrambled random words of the
   requested word types and returns a task of the puzzle string."
  [{:keys [instruments]
    :as   components} context word-types]
  (m/sp
    (let [scrambled-words (m/? (<scrambled-random-words components context word-types))]

      ;; Add event to span
      (span/add-span-data! {:context context
                            :event   {:name       "Completed setting puzzle"
                                      :attributes {:system/puzzle scrambled-words}}})

      ;; Update puzzle-size metric
      (instrument/record! (:puzzle-size-letters instruments)
                          {:context context
                           :value   (reduce + (map count scrambled-words))})

      (str/join " " scrambled-words))))
