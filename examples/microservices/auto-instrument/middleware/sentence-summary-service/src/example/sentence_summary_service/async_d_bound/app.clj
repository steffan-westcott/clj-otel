(ns example.sentence-summary-service.async-d-bound.app
  "Application logic, Manifold implementation using bound context."
  (:require [clojure.string :as str]
            [example.common.async.exec :as exec]
            [example.common.log4j2.utils :as log]
            [example.sentence-summary-service.async-d-bound.requests :as requests]
            [manifold.deferred :as d]
            [steffan-westcott.clj-otel.api.metrics.instrument :as instrument]
            [steffan-westcott.clj-otel.api.trace.d-span :as d-span]
            [steffan-westcott.clj-otel.api.trace.span :as span]))


(defn- <word-lengths
  "Get the word lengths and return a deferred of a list containing each word
   length."
  [components words]

  ;; Wrap channel with an asynchronous internal span.
  (d-span/async-bound-d-span ["Getting word lengths" {:system/words words}]

                             (apply d/zip
                                    (map (fn [word]
                                           (requests/<get-word-length components word))
                                         words))))



(defn- <summary
  "Returns a deferred of a summary of the given word lengths."
  [{:keys [instruments]} lengths]

  (d/future-with exec/cpu

    ;; Wrap synchronous function body with an internal span.
    (span/with-bound-span! ["Building sentence summary" {:system/word-lengths lengths}]

      (Thread/sleep 25) ; pretend to be CPU intensive
      (let [result {:words (count lengths)
                    :shortest-length (apply min lengths)
                    :longest-length (apply max lengths)}]

        ;; Creates log record with attributes log4j.map_message.words,
        ;; log4j.map_message.shortest_length and log4j.map_message.longest_length
        (log/debug (assoc result :message "Computed sentence summary"))

        ;; Add more attributes to internal span
        (span/add-span-data! {:attributes {:service.sentence-summary.summary/word-count (:words
                                                                                         result)}})

        ;; Update words-count metric
        (instrument/record! (:sentence-length instruments) {:value (count lengths)})

        result))))



(defn <build-summary
  "Builds a summary of the words in the sentence and returns a deferred of the
  summary value."
  [components sentence]
  (let [words (str/split sentence #",")]
    (-> (<word-lengths components words)
        (d/chain' (bound-fn [lengths]
                    (<summary components lengths))))))
