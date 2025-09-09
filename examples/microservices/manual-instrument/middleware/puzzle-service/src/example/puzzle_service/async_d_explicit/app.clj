(ns example.puzzle-service.async-d-explicit.app
  "Application logic, Manifold implementation using explicit context."
  (:require [clojure.string :as str]
            [example.puzzle-service.async-d-explicit.requests :as requests]
            [manifold.deferred :as d]
            [steffan-westcott.clj-otel.api.metrics.instrument :as instrument]
            [steffan-westcott.clj-otel.api.trace.d-span :as d-span]
            [steffan-westcott.clj-otel.api.trace.span :as span]))


(defn <scramble
  "Given a word, returns a deferred containing a string of the scrambled word."
  [context word]
  (d/future

    ;; Wrap synchronous function body with an internal span. Context containing
    ;; internal span is assigned to `context*`.
    (span/with-span-binding [context* {:parent     context
                                       :name       "Scrambling word"
                                       :attributes {:system/word word}}]

      (Thread/sleep 5)
      (let [scrambled-word (->> word
                                seq
                                shuffle
                                (apply str))]

        ;; Add more attributes to internal span
        (span/add-span-data! {:context    context*
                              :attributes {:service.puzzle/scrambled-word scrambled-word}})

        scrambled-word))))



(defn- <scrambled-random-words
  "Gets random words of the requested word types, scrambles them and returns
   a deferred of a list containing the scrambled words."
  [components context word-types]

  ;; Wrap channel with an asynchronous internal span. Context containing
  ;; internal span is assigned to `context*`.
  (d-span/d-span-binding [context* {:parent     context
                                    :name       "Getting scrambled random words"
                                    :attributes {:system/word-types word-types}}]

    (apply d/zip'
           (map (fn [word-type]
                  (-> (requests/<get-random-word components context* word-type)
                      (d/chain' (fn [word]
                                  (<scramble context* word)))))
                word-types))))



(defn <generate-puzzle
  "Constructs a puzzle string containing scrambled random words of the
   requested word types and returns a deferred of the puzzle string."
  [{:keys [instruments]
    :as   components} context word-types]
  (-> (<scrambled-random-words components context word-types)
      (d/chain' (fn [scrambled-words]

                  ;; Add event to span
                  (span/add-span-data! {:context context
                                        :event   {:name       "Completed setting puzzle"
                                                  :attributes {:system/puzzle scrambled-words}}})

                  ;; Update puzzle-size metric
                  (instrument/record! (:puzzle-size-letters instruments)
                                      {:context context
                                       :value   (reduce + (map count scrambled-words))})

                  (str/join " " scrambled-words)))))


