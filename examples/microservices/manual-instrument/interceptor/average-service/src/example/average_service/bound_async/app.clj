(ns example.average-service.bound-async.app
  "Application logic, bound async implementation."
  (:require [example.average-service.bound-async.requests :as requests]
            [example.common.core-async.utils :as async']
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

  ;; Start a new internal span that ends when the source channel (returned by
  ;; the body) closes or 3000 milliseconds have elapsed. Returns a dest channel
  ;; with buffer size 1. Values are piped from source to dest irrespective of
  ;; timeout.
  (async'/<with-bound-span ["Calculating average" {:system/nums nums}]
                           3000
                           1

                           (let [<sum (requests/<get-sum components nums)]
                             (async'/go-try
                               (divide (async'/<? <sum) (count nums))))))



(defn <averages
  "Calculates the averages of the odd numbers and the even numbers of nums and
   returns a channel of the result."
  [{:keys [instruments]
    :as   components} nums]
  (let [odds          (filter odd? nums)
        evens         (filter even? nums)
        <odds-average (when (seq odds)
                        (<average components odds))
        <evens-average (when (seq evens)
                         (<average components evens))]
    (async'/go-try
      (let [odds-average  (when <odds-average
                            (async'/<? <odds-average))
            evens-average (when <evens-average
                            (async'/<? <evens-average))
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
