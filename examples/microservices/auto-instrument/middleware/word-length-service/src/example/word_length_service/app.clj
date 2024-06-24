(ns example.word-length-service.app
  "Core application logic. This is a simple application which returns word
   lengths."
  (:require [steffan-westcott.clj-otel.api.metrics.instrument :as instrument]
            [steffan-westcott.clj-otel.api.trace.span :as span]))


(defn word-length
  "Returns length of the given `word`."
  [{:keys [instruments]} word]

  ;; Manually create an internal span that wraps body (lexical scope)
  (span/with-span! ["Calculating length" {:system/word word}]

    (Thread/sleep ^long (+ 50 (rand-int 80)))

    ;; Simulate an intermittent runtime exception. An uncaught exception leaving a span's scope
    ;; is reported as an exception event and the span status description is set to the
    ;; exception triage summary.
    (when (= "boom" word)
      (throw (RuntimeException. "Unable to process word")))

    (let [word-length (count word)]

      ;; Add an event to the current span with some attributes attached
      (span/add-event! "Calculated word length" {:system/word-length word-length})

      ;; Update letter-count metric
      (instrument/add! (:letters instruments) {:value word-length})

      word-length)))
