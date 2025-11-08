(ns example.sentence-summary-service.async-task-explicit.app
  "Application logic, Missionary implementation using explicit context."
  (:require [clojure.string :as str]
            [example.common.log4j2.utils :as log]
            [example.sentence-summary-service.async-task-explicit.requests :as requests]
            [missionary.core :as m]
            [steffan-westcott.clj-otel.api.metrics.instrument :as instrument]
            [steffan-westcott.clj-otel.api.trace.span :as span]
            [steffan-westcott.clj-otel.api.trace.task-span :as task-span]))


(defn- <word-lengths
  "Get the word lengths and return a task of a vector containing each word
   length."
  [components context words]

  ;; Wrap task with an asynchronous internal span. Context containing
  ;; internal span is assigned to `context*`.
  (task-span/task-span-binding [context* {:parent     context
                                          :name       "Getting word lengths"
                                          :attributes {:system/words words}}]

    (apply m/join
           vector
           (map (fn [word]
                  (requests/<get-word-length components context* word))
                words))))



(defn- <summary
  "Returns a task of summary of the given word lengths."
  [{:keys [instruments]} context lengths]
  (m/via m/cpu

    ;; Wrap synchronous function body with an internal span. Context containing
    ;; internal span is assigned to `context*`.
    (span/with-span-binding [context* {:parent     context
                                       :name       "Building sentence summary"
                                       :attributes {:system/word-lengths lengths}}]

      (Thread/sleep 25) ; pretend to be CPU intensive
      (let [result {:words (count lengths)
                    :shortest-length (apply min lengths)
                    :longest-length (apply max lengths)}]

        ;; Creates log record with attributes log4j.map_message.words,
        ;; log4j.map_message.shortest_length and log4j.map_message.longest_length
        (log/debug context* (assoc result :message "Computed sentence summary"))

        ;; Add more attributes to internal span
        (span/add-span-data! {:context    context*
                              :attributes {:service.sentence-summary.summary/word-count (:words
                                                                                         result)}})

        ;; Update words-count metric
        (instrument/record! (:sentence-length instruments)
                            {:context context*
                             :value   (count lengths)})

        result))))



(defn <build-summary
  "Builds a summary of the words in the sentence and returns a task of the
   summary value."
  [components context sentence]
  (let [words (str/split sentence #",")]
    (m/sp
      (let [lengths (m/? (<word-lengths components context words))]
        (m/? (<summary components context lengths))))))

