(ns example.sentence-summary-service.async-cf-bound.app
  "Application logic, async CompletableFuture implementation using bound
   context."
  (:require [clojure.string :as str]
            [example.sentence-summary-service.async-cf-bound.requests :as requests]
            [qbits.auspex :as aus]
            [steffan-westcott.clj-otel.api.metrics.instrument :as instrument]
            [steffan-westcott.clj-otel.api.trace.span :as span]))


(defn- <word-lengths
  "Given a collection of words, returns a `CompletableFuture` of a list
   containing each word length."
  [components words]

  ;; Wrap CompletableFuture with an asynchronous internal span.
  (span/async-bound-cf-span ["Getting word lengths" {:system/words words}]

                            (aus/all' (map (fn [word]
                                             (requests/<get-word-length components word))
                                           words))))



(defn- summary
  "Returns a summary of the given word lengths."
  [{:keys [instruments]} lengths]

  ;; Wrap synchronous function body with an internal span.
  (span/with-bound-span! ["Building sentence summary" {:system/word-lengths lengths}]

    (Thread/sleep 25)
    (let [result {:words (count lengths)
                  :shortest-length (apply min lengths)
                  :longest-length (apply max lengths)}]

      ;; Add more attributes to internal span
      (span/add-span-data! {:attributes {:service.sentence-summary.summary/word-count (:words
                                                                                       result)}})

      ;; Update words-count metric
      (instrument/record! (:sentence-length instruments) {:value (count lengths)})

      result)))



(defn <build-summary
  "Builds a summary of the words in the sentence and returns a
   `CompletableFuture` of the summary value."
  [components sentence]
  (let [words (str/split sentence #",")]
    (-> (<word-lengths components words)
        (aus/then (bound-fn [lengths]
                    (summary components lengths))))))

