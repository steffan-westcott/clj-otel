(ns example.sentence-summary-service.async-d-explicit.app
  "Application logic, Manifold implementation using explicit context."
  (:require [clojure.string :as str]
            [example.sentence-summary-service.async-d-explicit.requests :as requests]
            [manifold.deferred :as d]
            [steffan-westcott.clj-otel.api.metrics.instrument :as instrument]
            [steffan-westcott.clj-otel.api.trace.d-span :as d-span]
            [steffan-westcott.clj-otel.api.trace.span :as span]))


(defn- <word-lengths
  "Get the word lengths and return a deferred of a list containing each word
   length."
  [components context words]

  ;; Wrap deferred with an asynchronous internal span. Context containing
  ;; internal span is assigned to `context*`.
  (d-span/d-span-binding [context* {:parent     context
                                    :name       "Getting word lengths"
                                    :attributes {:system/words words}}]

    (apply d/zip
           (map (fn [word]
                  (requests/<get-word-length components context* word))
                words))))



(defn- summary
  "Returns a summary of the given word lengths."
  [{:keys [instruments]} context lengths]

  ;; Wrap synchronous function body with an internal span.
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
  "Builds a summary of the words in the sentence and returns a deferred of the
  summary value."
  [components context sentence]
  (let [words (str/split sentence #",")]
    (-> (<word-lengths components context words)
        (d/chain' (bound-fn [lengths]
                    (summary components context lengths))))))
