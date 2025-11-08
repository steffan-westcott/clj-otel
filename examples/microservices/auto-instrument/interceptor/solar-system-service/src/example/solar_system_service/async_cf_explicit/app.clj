(ns example.solar-system-service.async-cf-explicit.app
  "Application logic, async CompletableFuture implementation using explicit
   context."
  (:require [clojure.string :as str]
            [example.common.async.exec :as exec]
            [example.common.log4j2.utils :as log]
            [example.solar-system-service.async-cf-explicit.requests :as requests]
            [qbits.auspex :as aus]
            [steffan-westcott.clj-otel.api.metrics.instrument :as instrument]
            [steffan-westcott.clj-otel.api.trace.span :as span]))


(defn <planet-statistics
  "Get all statistics of a planet and return a `CompletableFuture` of a map of
   statistics."
  [components context planet]

  ;; Wrap CompletableFuture with an asynchronous internal span. Context containing
  ;; internal span is assigned to `context*`.
  (span/cf-span-binding [context* {:parent     context
                                   :name       "Getting planet statistics"
                                   :attributes {:system/planet planet}}]

    (-> (aus/all' (map (fn [statistic]
                         (requests/<get-statistic-value components context* planet statistic))
                       [:diameter :gravity]))
        (aus/then (fn [maps]
                    (apply merge maps))))))



(defn <format-report
  "Returns `CompletableFuture` of a report string of the given planet and
   statistic values."
  [{:keys [instruments]} context planet statistic-values]
  (aus/future
    (fn []

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

          report)))
    exec/cpu))



(defn <planet-report
  "Builds a report of planet statistics and results a `CompletableFuture` of the report
   string."
  [components context planet]
  (-> (<planet-statistics components context planet)
      (aus/fmap (fn [statistics]
                  (<format-report components context planet statistics)))))
