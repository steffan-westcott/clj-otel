(ns example.puzzle-service.bound-async.app
  "Application logic, bound async implementation."
  (:require [clojure.string :as str]
            [com.xadecimal.async-style :as style]
            [example.common.async.async-style :as style']
            [example.puzzle-service.bound-async.requests :as requests]
            [steffan-westcott.clj-otel.api.metrics.instrument :as instrument]
            [steffan-westcott.clj-otel.api.trace.span :as span]))


(defn <scramble
  "Given a word, returns a channel containing a string of the scrambled word.
   This 'CPU intensive' function runs on the compute-pool."
  [word]
  (style/compute

    ;; Wrap synchronous function body with an internal span.
    (span/with-bound-span! ["Scrambling word" {:system/word word}]

      (Thread/sleep 5)
      (let [scrambled-word (->> word
                                seq
                                shuffle
                                (apply str))]

        ;; Add more attributes to internal span
        (span/add-span-data! {:attributes {:service.puzzle/scrambled-word scrambled-word}})

        scrambled-word))))



(defn- <scrambled-random-words
  "Gets random words of the requested word types, scrambles them and returns
   a channel of a vector containing the scrambled words."
  [components word-types]

  ;; Wrap channel with an asynchronous internal span.
  (style'/async-bound-style-span ["Getting scrambled random words" {:system/word-types word-types}]

    (style/all (map (fn [word-type]
                      (-> (requests/<get-random-word components word-type)
                          (style/then (fn [word]
                                        (<scramble word)))))
                    word-types))))



(defn <generate-puzzle
  "Constructs a puzzle string containing scrambled random words of the
   requested word types and returns a channel of the puzzle string."
  [{:keys [instruments]
    :as   components} word-types]
  (-> (<scrambled-random-words components word-types)
      (style/then (fn [scrambled-words]

                    ;; Add event to span
                    (span/add-event! "Completed setting puzzle" {:system/puzzle scrambled-words})

                    ;; Update puzzle-size metric
                    (instrument/record! (:puzzle-size-letters instruments)
                                        {:value (reduce + (map count scrambled-words))})

                    (str/join " " scrambled-words)))))
