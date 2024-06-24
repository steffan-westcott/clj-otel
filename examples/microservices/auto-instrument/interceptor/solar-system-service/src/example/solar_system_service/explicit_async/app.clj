(ns example.solar-system-service.explicit-async.app
  "Application logic, explicit async implementation."
  (:require [clojure.core.async :as async]
            [clojure.string :as str]
            [example.common.core-async.utils :as async']
            [example.solar-system-service.explicit-async.requests :as requests]
            [steffan-westcott.clj-otel.api.metrics.instrument :as instrument]
            [steffan-westcott.clj-otel.api.trace.span :as span]))


(defn <planet-statistics
  "Get all statistics of a planet and return a channel containing a
   single-valued map values of each statistic."
  [components context planet]

  ;; Start a new internal span that ends when the source channel (returned by
  ;; the body) closes or 4000 milliseconds have elapsed. Returns a dest channel
  ;; with buffer size 2. Values are piped from source to dest irrespective of
  ;; timeout. Context containing internal span is assigned to `context*`.
  (async'/<with-span-binding [context* {:parent     context
                                        :name       "Getting planet statistics"
                                        :attributes {:system/planet planet}}]
    4000
    2

    (let [chs (map #(requests/<get-statistic-value components context* planet %)
                   [:diameter :gravity])]
      (async/merge chs))))



(defn format-report
  "Returns a report string of the given planet and statistic values."
  [{:keys [instruments]} context planet statistic-values]

  ;; Wrap synchronous function body with an internal span. Context containing
  ;; internal span is assigned to `context*`.
  (span/with-span-binding [context* {:parent     context
                                     :name       "Formatting report"
                                     :attributes {:system/planet planet
                                                  :service.solar-system.report/statistic-values
                                                  statistic-values}}]

    (Thread/sleep 25)
    (let [planet' (str/capitalize (name planet))
          {:keys [diameter gravity]} statistic-values
          report
          (str "The planet " planet' " has diameter " diameter "km and gravity " gravity "m/s^2.")]

      ;; Add more attributes to internal span
      (span/add-span-data! {:context    context*
                            :attributes {:service.solar-system.report/length (count report)}})

      ;; Update report-count metric
      (instrument/add! (:reports-created instruments)
                       {:context context*
                        :value   1})

      report)))



(defn <planet-report
  "Builds a report of planet statistics and results a channel of the report
   string."
  [components context planet]
  (let [<all-statistics   (<planet-statistics components context planet)
        <statistic-values (async'/<into?? {} <all-statistics)]
    (async'/go-try
      (try
        (let [statistics-values (async'/<? <statistic-values)]
          (format-report components context planet statistics-values))
        (finally
          (async'/close-and-drain!! <all-statistics))))))
