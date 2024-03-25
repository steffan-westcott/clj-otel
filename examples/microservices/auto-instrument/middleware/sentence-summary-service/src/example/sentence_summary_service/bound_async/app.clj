(ns example.sentence-summary-service.bound-async.app
  "Application logic, bound async implementation."
  (:require [clojure.core.async :as async]
            [clojure.string :as str]
            [example.common.core-async.utils :as async']
            [example.sentence-summary-service.bound-async.requests :as requests]
            [steffan-westcott.clj-otel.api.metrics.instrument :as instrument]
            [steffan-westcott.clj-otel.api.trace.span :as span]))


(defn- <word-lengths
  "Get the word lengths and return a channel containing a value for each word
  length."
  [components words]

  ;; Start a new internal span that ends when the source channel (returned by
  ;; the body) closes or 6000 milliseconds have elapsed. Returns a dest channel
  ;; with buffer size 3. Values are piped from source to dest irrespective of
  ;; timeout.
  (async'/<with-bound-span ["Getting word lengths" {:system/words words}]
                           6000
                           3

                           (async/merge (map #(requests/<get-word-length components %) words))))



(defn- summary
  "Returns a summary of the given word lengths."
  [{:keys [instruments]} lengths]

  ;; Wrap synchronous function body with an internal span.
  (span/with-bound-span! ["Building sentence summary" {:system/word-lengths lengths}]

    (Thread/sleep 25)
    (let [result {:word-count      (count lengths)
                  :shortest-length (apply min lengths)
                  :longest-length  (apply max lengths)}]

      ;; Add more attributes to internal span
      (span/add-span-data! {:attributes {:service.sentence-summary.summary/word-count (:word-count
                                                                                       result)}})

      ;; Update words-count metric
      (instrument/record! (:words-count instruments) {:value (count lengths)})

      result)))



(defn <build-summary
  "Builds a summary of the words in the sentence and returns a channel of the
  summary value."
  [components sentence]
  (let [words        (str/split sentence #"\s+")
        <all-lengths (<word-lengths components words)
        <lengths     (async'/<into?? [] <all-lengths)]
    (async'/go-try
      (try
        (let [lengths (async'/<? <lengths)]
          (summary components lengths))
        (finally
          (async'/close-and-drain!! <all-lengths))))))
