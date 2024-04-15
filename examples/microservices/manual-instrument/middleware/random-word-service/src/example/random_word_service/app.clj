(ns example.random-word-service.app
  "Core application logic. This is a simple application which returns random
   words."
  (:require [reitit.ring :as ring]
            [steffan-westcott.clj-otel.api.metrics.instrument :as instrument]
            [steffan-westcott.clj-otel.api.trace.span :as span]))


(def words
  "Map of word types and collections of random words of that type."
  {:noun      ["amusement" "bat" "cellar" "engine" "flesh" "frogs" "hearing" "record"]
   :verb      ["afford" "behave" "ignite" "justify" "race" "sprout" "strain" "wake"]
   :adjective ["cultured" "glorious" "grumpy" "handy" "kind" "lush" "mixed" "shut"]})



(defn random-word
  "Gets a random word of the requested type."
  [{:keys [instruments]} word-type]

  ;; Wrap the synchronous body in a new internal span.
  (span/with-span! ["Generating word" {:system/word-type word-type}]

    (Thread/sleep ^long (+ 10 (rand-int 80)))

    ;; Simulate an intermittent runtime exception. An uncaught exception leaving a span's scope
    ;; is reported as an exception event and the span status description is set to the
    ;; exception triage summary.
    (when (= :fault word-type)
      (throw (RuntimeException. "Processing fault")))

    (let [candidates (or (get words word-type)

                         ;; Exception data is added as attributes to the
                         ;; exception event by default.
                         (throw (ex-info
                                 "Unknown word type"
                                 {:type          ::ring/response
                                  :response      {:status 400
                                                  :body   {:error "Unknown word type"}}
                                  :service/error :service.random-word.errors/unknown-word-type
                                  :system/word-type word-type})))

          word       (rand-nth candidates)]

      ;; Add more attributes to the internal span
      (span/add-span-data! {:attributes {:system/word word}})

      ;; Update word-count metric
      (instrument/add! (:word-count instruments)
                       {:value      1
                        :attributes {:word-type word-type}})

      word)))
