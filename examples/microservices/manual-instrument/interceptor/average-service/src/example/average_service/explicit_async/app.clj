(ns example.average-service.explicit-async.app
  "Application logic, explicit async implementation."
  (:require [example.average-service.explicit-async.requests :as requests]
            [example.common.core-async.utils :as async']
            [steffan-westcott.clj-otel.api.metrics.instrument :as instrument]
            [steffan-westcott.clj-otel.api.trace.span :as span]))


(defn divide
  "Divides x by y."
  [context x y]

  ;; Wrap synchronous function body with an internal span. Context containing
  ;; internal span is assigned to `context*`.
  (span/with-span-binding [context* {:parent     context
                                     :name       "Calculating division"
                                     :attributes {:service.average.divide/parameters [x y]}}]

    (Thread/sleep 10)
    (let [result (double (/ x y))]

      ;; Add more attributes to internal span
      (span/add-span-data! {:context    context*
                            :attributes {:service.average.divide/result result}})

      result)))



(defn <average
  "Calculate the average of the nums and return a channel of the result."
  [components context nums]

  ;; Start a new internal span that ends when the source channel (returned by
  ;; the body) closes or 3000 milliseconds have elapsed. Returns a dest channel
  ;; with buffer size 1. Values are piped from source to dest irrespective of
  ;; timeout. Context containing internal span is assigned to `context*`.
  (async'/<with-span-binding [context* {:parent     context
                                        :name       "Calculating average"
                                        :attributes {:system/nums nums}}]
    3000
    1

    (let [<sum (requests/<get-sum components context* nums)]
      (async'/go-try
        (divide context* (async'/<? <sum) (count nums))))))



(defn <averages
  "Calculates the averages of the odd numbers and the even numbers of nums and
   returns a channel of the result."
  [{:keys [instruments]
    :as   components} context nums]
  (let [odds          (filter odd? nums)
        evens         (filter even? nums)
        <odds-average (when (seq odds)
                        (<average components context odds))
        <evens-average (when (seq evens)
                         (<average components context evens))]
    (async'/go-try
      (let [odds-average  (when <odds-average
                            (async'/<? <odds-average))
            evens-average (when <evens-average
                            (async'/<? <evens-average))
            result        {:odds  odds-average
                           :evens evens-average}]

        ;; Add event to span
        (span/add-span-data! {:context context
                              :event   {:name       "Finished calculations"
                                        :attributes {:system.averages/odds  odds-average
                                                     :system.averages/evens evens-average}}})

        ;; Update average-result metric
        (doseq [[partition average] result]
          (when average
            (instrument/record! (:average-result instruments)
                                {:context    context
                                 :value      average
                                 :attributes {:partition partition}})))

        result))))
