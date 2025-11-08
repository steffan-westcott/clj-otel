(ns example.puzzle-service.async-cf-explicit.app
  "Application logic, async CompletableFuture implementation using explicit
   context."
  (:require [clojure.string :as str]
            [example.common.async.exec :as exec]
            [example.common.log4j2.utils :as log]
            [example.puzzle-service.async-cf-explicit.requests :as requests]
            [qbits.auspex :as aus]
            [steffan-westcott.clj-otel.api.metrics.instrument :as instrument]
            [steffan-westcott.clj-otel.api.trace.span :as span]))


(defn- <scramble
  "Given a word, returns a `CompletableFuture` of a scrambled word string."
  [context word]
  (aus/future
    (fn []

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

          scrambled-word)))
    exec/cpu))



(defn- <scrambled-random-words
  "Gets random words of the requested word types, scrambles them and returns
   a `CompletableFuture` of a list containing the scrambled words."
  [components context word-types]

  ;; Wrap CompletableFuture with an asynchronous internal span. Context containing
  ;; internal span is assigned to `context*`.
  (span/cf-span-binding [context* {:parent     context
                                   :name       "Getting scrambled random words"
                                   :attributes {:system/word-types word-types}}]

    (aus/all' (map (fn [word-type]
                     (-> (requests/<get-random-word components context* word-type)
                         (aus/fmap (fn [word]
                                     (<scramble context* word)))))
                   word-types))))



(defn <generate-puzzle
  "Constructs a puzzle string containing scrambled random words of the
   requested word types and returns a `CompletableFuture` of the puzzle string."
  [{:keys [instruments]
    :as   components} context word-types]
  (-> (<scrambled-random-words components context word-types)
      (aus/then (fn [scrambled-words]

                  ;; Add event to span
                  (span/add-span-data! {:context context
                                        :event   {:name       "Completed setting puzzle"
                                                  :attributes {:system/puzzle scrambled-words}}})

                  ;; Update puzzle-size metric
                  (instrument/record! (:puzzle-size-letters instruments)
                                      {:context context
                                       :value   (reduce + (map count scrambled-words))})

                  (str/join " " scrambled-words)))))


