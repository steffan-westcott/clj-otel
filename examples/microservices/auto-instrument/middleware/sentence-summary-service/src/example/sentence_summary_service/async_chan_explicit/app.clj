(ns example.sentence-summary-service.async-chan-explicit.app
  "Application logic, core.async implementation using explicit context."
  (:require [clojure.string :as str]
            [com.xadecimal.async-style :as style]
            [example.common.log4j2.utils :as log]
            [example.sentence-summary-service.async-chan-explicit.requests :as requests]
            [steffan-westcott.clj-otel.api.metrics.instrument :as instrument]
            [steffan-westcott.clj-otel.api.trace.chan-span :as chan-span]
            [steffan-westcott.clj-otel.api.trace.span :as span]))


(defn- <word-lengths
  "Get the word lengths and return a channel of a vector containing each word
   length."
  [components context words]

  ;; Wrap channel with an asynchronous internal span. Context containing
  ;; internal span is assigned to `context*`.
  (chan-span/chan-span-binding [context* {:parent     context
                                          :name       "Getting word lengths"
                                          :attributes {:system/words words}}]

    (style/all (map (fn [word]
                      (requests/<get-word-length components context* word))
                    words))))



(defn- <summary
  "Returns a channel of a summary of the given word lengths."
  [{:keys [instruments]} context lengths]
  (style/compute

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
  "Builds a summary of the words in the sentence and returns a channel of the
   summary value."
  [components context sentence]
  (let [words (str/split sentence #",")]
    (-> (<word-lengths components context words)
        (style/then (fn [lengths]
                      (<summary components context lengths))))))
