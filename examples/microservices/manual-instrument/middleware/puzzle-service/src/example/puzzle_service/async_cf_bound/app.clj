(ns example.puzzle-service.async-cf-bound.app
  "Application logic, async CompletableFuture implementation using bound
   context."
  (:require [clojure.string :as str]
            [example.puzzle-service.async-cf-bound.requests :as requests]
            [qbits.auspex :as aus]
            [steffan-westcott.clj-otel.api.metrics.instrument :as instrument]
            [steffan-westcott.clj-otel.api.trace.span :as span]))


(defn- <scramble
  "Given a word, returns a `CompletableFuture` of a scrambled word string."
  [word]
  (aus/future (bound-fn []

                ;; Wrap synchronous function body with an internal span.
                (span/with-bound-span! ["Scrambling word" {:system/word word}]

                  (Thread/sleep 5)
                  (let [scrambled-word (->> word
                                            seq
                                            shuffle
                                            (apply str))]

                    ;; Add more attributes to internal span
                    (span/add-span-data! {:attributes {:service.puzzle/scrambled-word
                                                       scrambled-word}})

                    scrambled-word)))))



(defn- <scrambled-random-words
  "Gets random words of the requested word types, scrambles them and returns
   a `CompletableFuture` of a list containing the scrambled words."
  [components word-types]

  ;; Wrap CompletableFuture with an asynchronous internal span.
  (span/async-bound-cf-span ["Getting scrambled random words" {:system/word-types word-types}]

                            (aus/all' (map (fn [word-type]
                                             (-> (requests/<get-random-word components word-type)
                                                 (aus/fmap (bound-fn [word]
                                                             (<scramble word)))))
                                           word-types))))



(defn <generate-puzzle
  "Constructs a puzzle string containing scrambled random words of the
   requested word types and returns a `CompletableFuture` of the puzzle string."
  [{:keys [instruments]
    :as   components} word-types]
  (-> (<scrambled-random-words components word-types)
      (aus/then (bound-fn [scrambled-words]

                  ;; Add event to span
                  (span/add-event! "Completed setting puzzle" {:system/puzzle scrambled-words})

                  ;; Update puzzle-size metric
                  (instrument/record! (:puzzle-size-letters instruments)
                                      {:value (reduce + (map count scrambled-words))})

                  (str/join " " scrambled-words)))))

