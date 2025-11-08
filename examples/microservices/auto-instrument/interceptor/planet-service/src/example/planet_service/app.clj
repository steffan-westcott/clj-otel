(ns example.planet-service.app
  "Application logic. This is a simple application which returns planet
   statistics."
  (:require [example.common.log4j2.utils :as log]
            [steffan-westcott.clj-otel.api.metrics.instrument :as instrument]
            [steffan-westcott.clj-otel.api.trace.span :as span]))


(def planet-statistics
  "Map of planets and their statistics. Saturn is missing some data."
  {:mercury {:diameter 4879
             :gravity  3.7}
   :venus   {:diameter 12104
             :gravity  8.9}
   :earth   {:diameter 12756
             :gravity  9.8}
   :mars    {:diameter 6792
             :gravity  3.7}
   :jupiter {:diameter 142984
             :gravity  23.1}
   :saturn  {:diameter 120536
             :gravity  nil} ; missing gravity value
   :uranus  {:diameter 51118
             :gravity  8.7}
   :neptune {:diameter 49528
             :gravity  11.0}
   :pluto   {:diameter 2370
             :gravity  0.7}})



(defn planet-statistic
  "Returns a specific statistic value for a planet."
  [{:keys [instruments]} planet statistic]

  ;; Wrap the synchronous body in a new internal span.
  (span/with-span! ["Fetching statistic value"
                    {:system/planet    planet
                     :system/statistic statistic}]

    (Thread/sleep 50)
    (let [path [planet statistic]]
      (log/debug "Looking up" path)

      ;; Add an event to the current span with some attributes attached.
      (span/add-event! "Processed query path" {:service.planet/query-path path})

      ;; Update statistic-lookup-count metric
      (instrument/add! (:statistic-lookups instruments)
                       {:value      1
                        :attributes {:statistic statistic}})

      (if-let [result (get-in planet-statistics path)]

        result

        ;; Simulate an intermittent runtime exception when attempt is made to retrieve Saturn's
        ;; gravity value. An uncaught exception leaving a span's scope is reported as an
        ;; exception event and the span status description is set to the exception triage
        ;; summary.
        (throw (RuntimeException. "Failed to retrieve statistic"))))))
