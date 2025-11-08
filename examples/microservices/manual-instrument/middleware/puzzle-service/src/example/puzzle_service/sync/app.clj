(ns example.puzzle-service.sync.app
  "Application logic, synchronous implementation."
  (:require [clojure.string :as str]
            [example.common.log4j2.utils :as log]
            [example.puzzle-service.sync.requests :as requests]
            [steffan-westcott.clj-otel.api.metrics.instrument :as instrument]
            [steffan-westcott.clj-otel.api.trace.span :as span]))


(defn- scramble
  "Scrambles a given word."
  [word]
  (span/with-span! ["Scrambling word" {:system/word word}]

    (Thread/sleep 5) ; pretend to be CPU intensive
    (log/debug "About to scramble word:" word)
    (let [scrambled-word (->> word
                              seq
                              shuffle
                              (apply str))]
      (log/debug "Scrambled word:" scrambled-word)

      ;; Add more attributes to internal span
      (span/add-span-data! {:attributes {:service.puzzle/scrambled-word scrambled-word}})

      scrambled-word)))



(defn- scrambled-random-words
  "Gets random words of the requested word types, scrambles them and returns
   a vector containing the scrambled words."
  [components word-types]

  ;; Wrap the synchronous body in a new internal span.
  (span/with-span! ["Getting scrambled random words" {:system/word-types word-types}]

    ;; Use `mapv` to force all scrambled words to be realized within span
    (mapv (fn [word-type]
            (scramble (requests/get-random-word components word-type)))
          word-types)))



(defn generate-puzzle
  "Constructs a puzzle string containing scrambled random words of the
   requested word types."
  [{:keys [instruments]
    :as   components} word-types]
  (let [scrambled-words (scrambled-random-words components word-types)]

    ;; Add event to span
    (span/add-event! "Completed setting puzzle" {:system/puzzle scrambled-words})

    ;; Update puzzle-size metric
    (instrument/record! (:puzzle-size-letters instruments)
                        {:value (reduce + (map count scrambled-words))})

    (str/join " " scrambled-words)))
