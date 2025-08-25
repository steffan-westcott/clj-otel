(ns example.average-service.async-chan-bound.app
  "Application logic, core.async implementation using bound context."
  (:require [com.xadecimal.async-style :as style]
            [example.average-service.async-chan-bound.requests :as requests]
            [example.common.async.async-style :as style']
            [steffan-westcott.clj-otel.api.metrics.instrument :as instrument]
            [steffan-westcott.clj-otel.api.trace.span :as span]))


(defn divide
  "Divides x by y."
  [x y]

  ;; Wrap synchronous function body with an internal span.
  (span/with-bound-span! ["Calculating division" {:service.average.divide/parameters [x y]}]

    (Thread/sleep 10)
    (let [result (double (/ x y))]

      ;; Add more attributes to internal span
      (span/add-span-data! {:attributes {:service.average.divide/result result}})

      result)))



(defn <average
  "Calculate the average of the nums and return a channel of the result."
  [components nums]

  ;; Wrap channel with an asynchronous internal span.
  (style'/async-bound-style-span ["Calculating average" {:system/nums nums}]

    (let [<sum (requests/<get-sum components nums)]
      (style/async
        (divide (style/await <sum) (count nums))))))



(defn <averages
  "Calculates the averages of the odd numbers and the even numbers of nums and
   returns a channel of the result."
  [{:keys [instruments]
    :as   components} nums]
  (let [odds  (filter odd? nums)
        evens (filter even? nums)]
    (style/async
      (style/clet [odds-average  (when (seq odds)
                                   (<average components odds))
                   evens-average (when (seq evens)
                                   (<average components evens))
                   result        {:odds  odds-average
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

        result))))
