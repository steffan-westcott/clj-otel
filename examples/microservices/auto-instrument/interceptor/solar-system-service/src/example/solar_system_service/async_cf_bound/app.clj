(ns example.solar-system-service.async-cf-bound.app
  "Application logic, async CompletableFuture implementation using bound
   context."
  (:require [clojure.string :as str]
            [example.common.async.exec :as exec]
            [example.solar-system-service.async-cf-bound.requests :as requests]
            [qbits.auspex :as aus]
            [steffan-westcott.clj-otel.api.metrics.instrument :as instrument]
            [steffan-westcott.clj-otel.api.trace.span :as span]))


(defn <planet-statistics
  "Get all statistics of a planet and return a `CompletableFuture` of a map of
   statistics."
  [components planet]

  ;; Wrap CompletableFuture with an asynchronous internal span.
  (span/async-bound-cf-span ["Getting planet statistics" {:system/planet planet}]

                            (-> (aus/all'
                                 (map (fn [statistic]
                                        (requests/<get-statistic-value components planet statistic))
                                      [:diameter :gravity]))
                                (aus/then (fn [maps]
                                            (apply merge maps))))))



(defn <format-report
  "Returns `CompletableFuture` of a report string of the given planet and
   statistic values."
  [{:keys [instruments]} planet statistic-values]
  (aus/future
    (bound-fn []

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

          report)))
    exec/cpu))



(defn <planet-report
  "Builds a report of planet statistics and returns a `CompletableFuture` of the report
   string."
  [components planet]
  (-> (<planet-statistics components planet)
      (aus/fmap (bound-fn [statistics]
                  (<format-report components planet statistics)))))
