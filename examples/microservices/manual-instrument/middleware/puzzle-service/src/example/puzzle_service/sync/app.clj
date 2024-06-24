(ns example.puzzle-service.sync.app
  "Application logic, synchronous implementation."
  (:require [clojure.string :as str]
            [example.puzzle-service.sync.requests :as requests]
            [steffan-westcott.clj-otel.api.metrics.instrument :as instrument]
            [steffan-westcott.clj-otel.api.trace.span :as span]))


(defn- random-words
  "Get random words of the requested types."
  [components word-types]

  ;; Wrap the synchronous body in a new internal span.
  (span/with-span! ["Getting random words" {:system/word-types word-types}]

    ;; Use `doall` to force lazy sequence to be realized within span
    (doall (map #(requests/get-random-word components %) word-types))))



(defn- scramble
  "Scrambles a given word."
  [word]
  (span/with-span! ["Scrambling word" {:system/word word}]

    (Thread/sleep 5)
    (let [scrambled-word (->> word
                              seq
                              shuffle
                              (apply str))]

      ;; Add more attributes to internal span
      (span/add-span-data! {:attributes {:service.puzzle/scrambled-word scrambled-word}})

      scrambled-word)))



(defn generate-puzzle
  "Constructs a puzzle string containing scrambled random words of the
   requested word types."
  [{:keys [instruments]
    :as   components} word-types]
  (let [words (random-words components word-types)
        scrambled-words (map scramble words)]

    ;; Add event to span
    (span/add-event! "Completed setting puzzle" {:system/puzzle scrambled-words})

    ;; Update puzzle-size metric
    (instrument/record! (:puzzle-size-letters instruments)
                        {:value (reduce + (map count scrambled-words))})

    (str/join " " scrambled-words)))
