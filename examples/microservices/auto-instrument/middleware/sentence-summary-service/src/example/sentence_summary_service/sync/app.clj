(ns example.sentence-summary-service.sync.app
  "Application logic, synchronous implementation."
  (:require [clojure.string :as str]
            [example.common.log4j2.utils :as log]
            [example.sentence-summary-service.sync.requests :as requests]
            [steffan-westcott.clj-otel.api.metrics.instrument :as instrument]
            [steffan-westcott.clj-otel.api.trace.span :as span]))


(defn- word-lengths
  "Get the word lengths and return a vector containing each word length."
  [components words]

  ;; Wrap synchronous function body with an internal span.
  (span/with-span! ["Getting word lengths" {:system/words words}]

    ;; Use `mapv` to force all word lengths to be realized within span
    (mapv (fn [word]
            (requests/get-word-length components word))
          words)))



(defn- summary
  "Returns a summary of the given word lengths."
  [{:keys [instruments]} lengths]

  ;; Wrap synchronous function body with an internal span.
  (span/with-span! ["Building sentence summary" {:system/word-lengths lengths}]

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

      result)))



(defn build-summary
  "Builds a summary of the words in the sentence."
  [components sentence]
  (let [words   (str/split sentence #",")
        lengths (word-lengths components words)]
    (summary components lengths)))
