(ns example.average-service.async-chan-explicit.app
  "Application logic, core.async implementation using explicit context."
  (:require [com.xadecimal.async-style :as style]
            [example.average-service.async-chan-explicit.requests :as requests]
            [steffan-westcott.clj-otel.api.metrics.instrument :as instrument]
            [steffan-westcott.clj-otel.api.trace.span :as span]
            [steffan-westcott.clj-otel.api.trace.style-span :as sspan]))


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

  ;; Wrap channel with an asynchronous internal span. Context containing
  ;; internal span is assigned to `context*`.
  (sspan/style-span-binding [context* {:parent     context
                                       :name       "Calculating average"
                                       :attributes {:system/nums nums}}]

    (let [<sum (requests/<get-sum components context* nums)]
      (style/async
        (divide context* (style/await <sum) (count nums))))))



(defn <averages
  "Calculates the averages of the odd numbers and the even numbers of nums and
   returns a channel of the result."
  [{:keys [instruments]
    :as   components} context nums]
  (let [odds  (filter odd? nums)
        evens (filter even? nums)]
    (style/async
      (style/clet [odds-average  (when (seq odds)
                                   (<average components context odds))
                   evens-average (when (seq evens)
                                   (<average components context evens))
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
