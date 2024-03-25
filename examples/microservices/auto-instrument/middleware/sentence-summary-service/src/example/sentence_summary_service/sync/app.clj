(ns example.sentence-summary-service.sync.app
  "Application logic, synchronous implementation."
  (:require [clojure.string :as str]
            [example.sentence-summary-service.sync.requests :as requests]
            [steffan-westcott.clj-otel.api.metrics.instrument :as instrument]
            [steffan-westcott.clj-otel.api.trace.span :as span]))


(defn- word-lengths
  "Get the word lengths."
  [components words]

  ;; Wrap synchronous function body with an internal span.
  (span/with-span! ["Getting word lengths" {:system/words words}]

    ;; Use `doall` to force lazy sequence to be realized within span
    (doall (map #(requests/get-word-length components %) words))))



(defn- summary
  "Returns a summary of the given word lengths."
  [{:keys [instruments]} lengths]

  ;; Wrap synchronous function body with an internal span.
  (span/with-span! ["Building sentence summary" {:system/word-lengths lengths}]

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



(defn build-summary
  "Builds a summary of the words in the sentence."
  [components sentence]
  (let [words   (str/split sentence #"\s+")
        lengths (word-lengths components words)]
    (summary components lengths)))
