(ns example.puzzle-service.explicit-async.app
  "Application logic, explicit async implementation."
  (:require [clojure.string :as str]
            [example.common.core-async.utils :as async']
            [example.puzzle-service.explicit-async.requests :as requests]
            [steffan-westcott.clj-otel.api.metrics.instrument :as instrument]
            [steffan-westcott.clj-otel.api.trace.span :as span])
  (:import (clojure.lang PersistentQueue)))


(defn- <random-words
  "Get random words of the requested types and return a channel containing
   a value for each word."
  [components context word-types]

  ;; Start a new internal span that ends when the source channel (returned by
  ;; the body) closes or 5000 milliseconds have elapsed. Returns a dest channel
  ;; with buffer size 2. Values are piped from source to dest irrespective of
  ;; timeout. Context containing internal span is assigned to `context*`.
  (async'/<with-span-binding [context* {:parent     context
                                        :name       "Getting random words"
                                        :attributes {:system/word-types word-types}}]
    5000
    2

    (let [<words* (map #(requests/<get-random-word components context* %) word-types)]
      (async'/<concat <words*))))



(defn- scramble
  "Scrambles a given word."
  [context word]

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

      scrambled-word)))



(defn <generate-puzzle
  "Constructs a puzzle string containing scrambled random words of the
   requested word types and returns a channel of the puzzle string."
  [{:keys [instruments]
    :as   components} context word-types]
  (let [<words (<random-words components context word-types)]
    (async'/go-try
      (try
        (loop [scrambled-words PersistentQueue/EMPTY]
          (if-let [word (async'/<? <words)]
            (recur (conj scrambled-words (scramble context word)))
            (do

              ;; Add event to span
              (span/add-span-data! {:context context
                                    :event   {:name       "Completed setting puzzle"
                                              :attributes {:system/puzzle scrambled-words}}})

              ;; Update puzzle-size metric
              (instrument/record! (:puzzle-size instruments)
                                  {:context context
                                   :value   (reduce + (map count scrambled-words))})

              (str/join " " scrambled-words))))
        (finally
          (async'/close-and-drain!! <words))))))
