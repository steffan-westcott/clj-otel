(ns example.solar-system-service.async-chan-explicit.app
  "Application logic, core.async implementation using explicit context."
  (:require [clojure.string :as str]
            [com.xadecimal.async-style :as style]
            [example.solar-system-service.async-chan-explicit.requests :as requests]
            [steffan-westcott.clj-otel.api.metrics.instrument :as instrument]
            [steffan-westcott.clj-otel.api.trace.span :as span]
            [steffan-westcott.clj-otel.api.trace.style-span :as sspan]))


(defn <planet-statistics
  "Get all statistics of a planet and return a channel of a map of statistics."
  [components context planet]

  ;; Wrap channel with an asynchronous internal span. Context containing
  ;; internal span is assigned to `context*`.
  (sspan/style-span-binding [context* {:parent     context
                                       :name       "Getting planet statistics"
                                       :attributes {:system/planet planet}}]

    (-> (style/all (map (fn [statistic]
                          (requests/<get-statistic-value components context* planet statistic))
                        [:diameter :gravity]))
        (style/then #(apply merge %)))))



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
  "Builds a report of planet statistics and returns a channel of the report
   string."
  [components context planet]
  (-> (<planet-statistics components context planet)
      (style/then (fn [statistics]
                    (format-report components context planet statistics)))))
