(ns example.solar-system-service.async-chan-bound.app
  "Application logic, core.async implementation using bound context."
  (:require [clojure.string :as str]
            [com.xadecimal.async-style :as style]
            [example.solar-system-service.async-chan-bound.requests :as requests]
            [steffan-westcott.clj-otel.api.metrics.instrument :as instrument]
            [steffan-westcott.clj-otel.api.trace.chan-span :as chan-span]
            [steffan-westcott.clj-otel.api.trace.span :as span]))


(defn <planet-statistics
  "Get all statistics of a planet and return a channel of a map of statistics."
  [components planet]

  ;; Wrap channel with an asynchronous internal span.
  (chan-span/async-bound-chan-span
    ["Getting planet statistics" {:system/planet planet}]

    (-> (style/all (map (fn [statistic]
                          (requests/<get-statistic-value components planet statistic))
                        [:diameter :gravity]))
        (style/then #(apply merge %)))))



(defn format-report
  "Returns a report string of the given planet and statistic values."
  [{:keys [instruments]} planet statistic-values]

  ;; Wrap synchronous function body with an internal span.
  (span/with-bound-span! ["Formatting report"
                          {:system/planet planet
                           :service.solar-system.report/statistic-values statistic-values}]

    (Thread/sleep 25)
    (let [planet' (str/capitalize (name planet))
          {:keys [diameter gravity]} statistic-values
          report
          (str "The planet " planet' " has diameter " diameter "km and gravity " gravity "m/s^2.")]

      ;; Add more attributes to internal span
      (span/add-span-data! {:attributes {:service.solar-system.report/length (count report)}})

      ;; Update report-count metric
      (instrument/add! (:reports-created instruments) {:value 1})

      report)))



(defn <planet-report
  "Builds a report of planet statistics and results a channel of the report
   string."
  [components planet]
  (-> (<planet-statistics components planet)
      (style/then (fn [statistics]
                    (format-report components planet statistics)))))
