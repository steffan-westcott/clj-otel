(ns example.solar-system-service.async-task-explicit.app
  "Application logic, Missionary implementation using explicit context."
  (:require [clojure.string :as str]
            [example.common.log4j2.utils :as log]
            [example.solar-system-service.async-task-explicit.requests :as requests]
            [missionary.core :as m]
            [steffan-westcott.clj-otel.api.metrics.instrument :as instrument]
            [steffan-westcott.clj-otel.api.trace.span :as span]
            [steffan-westcott.clj-otel.api.trace.task-span :as task-span]))


(defn <planet-statistics
  "Get all statistics of a planet and return a task of a map of statistics."
  [components context planet]

  ;; Wrap task with an asynchronous internal span. Context containing
  ;; internal span is assigned to `context*`.
  (task-span/task-span-binding [context* {:parent     context
                                          :name       "Getting planet statistics"
                                          :attributes {:system/planet planet}}]

    (apply m/join
           merge
           (map (fn [statistic]
                  (requests/<get-statistic-value components context* planet statistic))
                [:diameter :gravity]))))



(defn <format-report
  "Returns a task of a report string of the given planet and statistic values."
  [{:keys [instruments]} context planet statistic-values]
  (m/via m/cpu

    ;; Wrap synchronous function body with an internal span. Context containing
    ;; internal span is assigned to `context*`.
    (span/with-span-binding [context* {:parent     context
                                       :name       "Formatting report"
                                       :attributes {:system/planet planet
                                                    :service.solar-system.report/statistic-values
                                                    statistic-values}}]

      ;; Creates a log record with attributes log4j.map_message.planet and
      ;; log4j.map_message.stats
      (log/debug context*
                 {:message "Formatting statistics"
                  :planet  planet
                  :stats   statistic-values})

      (Thread/sleep 25) ; pretend to be CPU intensive
      (let [planet' (str/capitalize (name planet))
            {:keys [diameter gravity]} statistic-values
            report  (str "The planet "
                         planet'
                         " has diameter "
                         diameter
                         "km and gravity "
                         gravity
                         "m/s^2.")]

        ;; Add more attributes to internal span
        (span/add-span-data! {:context    context*
                              :attributes {:service.solar-system.report/length (count report)}})

        ;; Update report-count metric
        (instrument/add! (:reports-created instruments)
                         {:context context*
                          :value   1})

        report))))



(defn <planet-report
  "Builds a report of planet statistics and returns a task of the report
   string."
  [components context planet]
  (m/sp
    (let [statistics (m/? (<planet-statistics components context planet))]
      (m/? (<format-report components context planet statistics)))))
