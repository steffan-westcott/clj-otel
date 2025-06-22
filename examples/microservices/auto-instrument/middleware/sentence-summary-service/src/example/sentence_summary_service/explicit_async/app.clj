(ns example.sentence-summary-service.explicit-async.app
  "Application logic, explicit async implementation."
  (:require [clojure.core.async :as async]
            [clojure.string :as str]
            [example.common.core-async.utils :as async']
            [example.sentence-summary-service.explicit-async.requests :as requests]
            [steffan-westcott.clj-otel.api.metrics.instrument :as instrument]
            [steffan-westcott.clj-otel.api.trace.span :as span]))


(defn- <word-lengths
  "Get the word lengths and return a channel containing a value for each word
   length."
  [components context words]

  ;; Start a new internal span that ends when the source channel (returned by
  ;; the body) closes or 6000 milliseconds have elapsed. Returns a dest channel
  ;; with buffer size 3. Values are piped from source to dest irrespective of
  ;; timeout. Context containing internal span is assigned to `context*`.
  (async'/<with-span-binding [context* {:parent     context
                                        :name       "Getting word lengths"
                                        :attributes {:system/words words}}]
    6000
    3

    (let [chs (map #(requests/<get-word-length components context* %) words)]
      (async/merge chs))))



(defn- summary
  "Returns a summary of the given word lengths."
  [{:keys [instruments]} context lengths]

  ;; Wrap synchronous function body with an internal span. Context containing
  ;; internal span is assigned to `context*`.
  (span/with-span-binding [context* {:parent     context
                                     :name       "Building sentence summary"
                                     :attributes {:system/word-lengths lengths}}]

    (Thread/sleep 25)
    (let [result {:words (count lengths)
                  :shortest-length (apply min lengths)
                  :longest-length (apply max lengths)}]

      ;; Add more attributes to internal span
      (span/add-span-data! {:context    context*
                            :attributes {:service.sentence-summary.summary/word-count (:words
                                                                                       result)}})

      ;; Update words-count metric
      (instrument/record! (:sentence-length instruments)
                          {:context context*
                           :value   (count lengths)})

      result)))



(defn <build-summary
  "Builds a summary of the words in the sentence and returns a channel of the
   summary value."
  [components context sentence]
  (let [words        (str/split sentence #",")
        <all-lengths (<word-lengths components context words)
        <lengths     (async'/<into?? [] <all-lengths)]
    (async'/go-try
      (try
        (let [lengths (async'/<? <lengths)]
          (summary components context lengths))
        (finally
          (async'/close-and-drain!! <all-lengths))))))
