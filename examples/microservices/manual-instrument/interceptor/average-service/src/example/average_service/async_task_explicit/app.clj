(ns example.average-service.async-task-explicit.app
  "Application logic, Missionary implementation using explicit context."
  (:require [example.average-service.async-task-explicit.requests :as requests]
            [example.common.log4j2.utils :as log]
            [missionary.core :as m]
            [steffan-westcott.clj-otel.api.metrics.instrument :as instrument]
            [steffan-westcott.clj-otel.api.trace.span :as span]
            [steffan-westcott.clj-otel.api.trace.task-span :as task-span]))


(defn <divide
  "Divides x by y, returns a task of the result."
  [context x y]
  (m/via m/cpu

    ;; Wrap synchronous function body with an internal span. Context containing
    ;; internal span is assigned to `context*`.
    (span/with-span-binding [context* {:parent     context
                                       :name       "Calculating division"
                                       :attributes {:service.average.divide/parameters [x y]}}]

      (Thread/sleep 10) ; pretend to be CPU intensive
      (let [result (double (/ x y))]
        (log/debug context* "Divided" x "by" y "to give" result)

        ;; Add more attributes to internal span
        (span/add-span-data! {:context    context*
                              :attributes {:service.average.divide/result result}})

        result))))



(defn <average
  "Calculate the average of the nums and return a task of the result."
  [components context nums]

  ;; Wrap task with an asynchronous internal span. Context containing
  ;; internal span is assigned to `context*`.
  (task-span/task-span-binding [context* {:parent     context
                                          :name       "Calculating average"
                                          :attributes {:system/nums nums}}]

    (m/sp
      (let [sum (m/? (requests/<get-sum components context* nums))]
        (m/? (<divide context* sum (count nums)))))))



(defn <averages
  "Calculates the averages of the odd numbers and the even numbers of nums and
   returns a task of the result."
  [{:keys [instruments]
    :as   components} context nums]
  (let [odds  (filter odd? nums)
        evens (filter even? nums)]
    (m/sp
      (let [[odds-average evens-average] (m/? (m/join vector
                                                      (if (seq odds)
                                                        (<average components context odds)
                                                        (m/sp))
                                                      (if (seq evens)
                                                        (<average components context evens)
                                                        (m/sp))))
            result {:odds  odds-average
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

