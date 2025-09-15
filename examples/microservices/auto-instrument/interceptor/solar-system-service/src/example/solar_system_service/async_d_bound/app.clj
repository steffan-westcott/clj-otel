(ns example.solar-system-service.async-d-bound.app
  "Application logic, async Manifold implementation using bound
   context."
  (:require [clojure.string :as str]
            [example.common.async.exec :as exec]
            [example.solar-system-service.async-d-bound.requests :as requests]
            [manifold.deferred :as d]
            [steffan-westcott.clj-otel.api.metrics.instrument :as instrument]
            [steffan-westcott.clj-otel.api.trace.d-span :as d-span]
            [steffan-westcott.clj-otel.api.trace.span :as span]))


(defn <planet-statistics
  "Get all statistics of a planet and return a deferred of a map of
   statistics."
  [components planet]

  ;; Wrap deferred with an asynchronous internal span.
  (d-span/async-bound-d-span
   ["Getting planet statistics" {:system/planet planet}]

   (-> (apply d/zip'
              (map (fn [statistic]
                     (requests/<get-statistic-value components planet statistic))
                   [:diameter :gravity]))
       (d/chain' (fn [maps]
                   (apply merge maps))))))



(defn <format-report
  "Returns a deferred of a report string of the given planet and statistic values."
  [{:keys [instruments]} planet statistic-values]
  (d/future-with exec/cpu

    ;; Wrap synchronous function body with an internal span.
    (span/with-bound-span! ["Formatting report"
                            {:system/planet planet
                             :service.solar-system.report/statistic-values statistic-values}]

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
        (span/add-span-data! {:attributes {:service.solar-system.report/length (count report)}})

        ;; Update report-count metric
        (instrument/add! (:reports-created instruments) {:value 1})

        report))))



(defn <planet-report
  "Builds a report of planet statistics and returns a deferred of the report
   string."
  [components planet]
  (-> (<planet-statistics components planet)
      (d/chain' (bound-fn [statistics]
                  (<format-report components planet statistics)))))
