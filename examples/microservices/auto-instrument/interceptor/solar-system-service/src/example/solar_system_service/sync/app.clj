(ns example.solar-system-service.sync.app
  "Application logic, synchronous implementation."
  (:require [clojure.string :as str]
            [example.solar-system-service.sync.requests :as requests]
            [steffan-westcott.clj-otel.api.metrics.instrument :as instrument]
            [steffan-westcott.clj-otel.api.trace.span :as span]))


(defn- planet-statistics
  "Get all statistics of a planet and return single-valued map values of each
   statistic."
  [components planet]

  ;; Wrap synchronous function body with an internal span.
  (span/with-span! ["Getting planet statistics" {:system/planet planet}]

    ;; Use `doall` to force lazy sequence to be realized within span
    (doall (map #(requests/get-statistic-value components planet %) [:diameter :gravity]))))



(defn- format-report
  "Returns a report string of the given planet and statistic values."
  [{:keys [instruments]} planet statistic-values]

  ;; Wrap synchronous function body with an internal span.
  (span/with-span! ["Formatting report"
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



(defn planet-report
  "Builds a report of planet statistics and returns report string."
  [components planet]
  (let [all-statistics   (planet-statistics components planet)
        statistic-values (into {} all-statistics)]
    (format-report components planet statistic-values)))
