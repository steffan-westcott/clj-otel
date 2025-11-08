(ns example.average-service.async-chan-bound.app
  "Application logic, core.async implementation using bound context."
  (:require [com.xadecimal.async-style :as style]
            [example.average-service.async-chan-bound.requests :as requests]
            [example.common.log4j2.utils :as log]
            [steffan-westcott.clj-otel.api.metrics.instrument :as instrument]
            [steffan-westcott.clj-otel.api.trace.chan-span :as chan-span]
            [steffan-westcott.clj-otel.api.trace.span :as span]))


(defn <divide
  "Divides x by y and returns a channel of the result."
  [x y]
  (style/compute

    ;; Wrap synchronous function body with an internal span.
    (span/with-bound-span! ["Calculating division" {:service.average.divide/parameters [x y]}]

      (Thread/sleep 10) ; pretend to be CPU intensive
      (let [result (double (/ x y))]
        (log/debug "Divided" x "by" y "to give" result)

        ;; Add more attributes to internal span
        (span/add-span-data! {:attributes {:service.average.divide/result result}})

        result))))



(defn <average
  "Calculate the average of the nums and return a channel of a 1-element vector
   of the result."
  [components nums]

  ;; Wrap channel with an asynchronous internal span.
  (chan-span/async-bound-chan-span ["Calculating average" {:system/nums nums}]

    (if (seq nums)
      (-> (requests/<get-sum components nums)
          (style/then (fn [sum]
                        (<divide sum (count nums))))
          (style/then vector))
      (style/async
        [nil]))))



(defn <averages
  "Calculates the averages of the odd numbers and the even numbers of nums and
   returns a channel of the result."
  [{:keys [instruments]
    :as   components} nums]
  (-> (style/all [(<average components (filter odd? nums))
                  (<average components (filter even? nums))])
      (style/then
       (fn [[[odds-average] [evens-average]]]
         (let [result {:odds  odds-average
                       :evens evens-average}]

           ;; Add event to span
           (span/add-event! "Finished calculations"
                            {:system.averages/odds  odds-average
                             :system.averages/evens evens-average})

           ;; Update average-result metric
           (doseq [[partition average] result]
             (when average
               (instrument/record! (:average-result instruments)
                                   {:value      average
                                    :attributes {:partition partition}})))

           result)))))
