(ns example.puzzle-service.async-d-bound.app
  "Application logic, Manifold implementation using bound context."
  (:require [clojure.string :as str]
            [example.common.async.exec :as exec]
            [example.common.log4j2.utils :as log]
            [example.puzzle-service.async-d-bound.requests :as requests]
            [manifold.deferred :as d]
            [steffan-westcott.clj-otel.api.metrics.instrument :as instrument]
            [steffan-westcott.clj-otel.api.trace.d-span :as d-span]
            [steffan-westcott.clj-otel.api.trace.span :as span]))


(defn <scramble
  "Given a word, returns a deferred containing a string of the scrambled word."
  [word]
  (d/future-with exec/cpu

    ;; Wrap synchronous function body with an internal span.
    (span/with-bound-span! ["Scrambling word" {:system/word word}]

      (Thread/sleep 5) ; pretend to be CPU intensive
      (log/debug "About to scramble word:" word)
      (let [scrambled-word (->> word
                                seq
                                shuffle
                                (apply str))]
        (log/debug "Scrambled word:" scrambled-word)

        ;; Add more attributes to internal span
        (span/add-span-data! {:attributes {:service.puzzle/scrambled-word scrambled-word}})

        scrambled-word))))



(defn- <scrambled-random-words
  "Gets random words of the requested word types, scrambles them and returns
   a deferred of a list containing the scrambled words."
  [components word-types]

  ;; Wrap channel with an asynchronous internal span.
  (d-span/async-bound-d-span ["Getting scrambled random words" {:system/word-types word-types}]

                             (apply d/zip'
                                    (map (fn [word-type]
                                           (-> (requests/<get-random-word components word-type)
                                               (d/chain' (bound-fn [word]
                                                           (<scramble word)))))
                                         word-types))))



(defn <generate-puzzle
  "Constructs a puzzle string containing scrambled random words of the
   requested word types and returns a deferred of the puzzle string."
  [{:keys [instruments]
    :as   components} word-types]
  (-> (<scrambled-random-words components word-types)
      (d/chain' (bound-fn [scrambled-words]

                  ;; Add event to span
                  (span/add-event! "Completed setting puzzle" {:system/puzzle scrambled-words})

                  ;; Update puzzle-size metric
                  (instrument/record! (:puzzle-size-letters instruments)
                                      {:value (reduce + (map count scrambled-words))})

                  (str/join " " scrambled-words)))))
