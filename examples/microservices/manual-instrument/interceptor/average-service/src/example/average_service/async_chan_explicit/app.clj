(ns example.average-service.async-chan-explicit.app
  "Application logic, core.async implementation using explicit context."
  (:require [com.xadecimal.async-style :as style]
            [example.average-service.async-chan-explicit.requests :as requests]
            [example.common.log4j2.utils :as log]
            [steffan-westcott.clj-otel.api.metrics.instrument :as instrument]
            [steffan-westcott.clj-otel.api.trace.chan-span :as chan-span]
            [steffan-westcott.clj-otel.api.trace.span :as span]))


(defn <divide
  "Divides x by y and returns a channel of the result."
  [context x y]
  (style/compute

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
  "Calculate the average of the nums and return a channel of a 1-element vector
   of the result."
  [components context nums]

  ;; Wrap channel with an asynchronous internal span. Context containing
  ;; internal span is assigned to `context*`.
  (chan-span/chan-span-binding [context* {:parent     context
                                          :name       "Calculating average"
                                          :attributes {:system/nums nums}}]

    (if (seq nums)
      (-> (requests/<get-sum components context* nums)
          (style/then (fn [sum]
                        (<divide context* sum (count nums))))
          (style/then vector))
      (style/async
        [nil]))))



(defn <averages
  "Calculates the averages of the odd numbers and the even numbers of nums and
   returns a channel of the result."
  [{:keys [instruments]
    :as   components} context nums]
  (-> (style/all [(<average components context (filter odd? nums))
                  (<average components context (filter even? nums))])
      (style/then
       (fn [[[odds-average] [evens-average]]]
         (let [result {:odds  odds-average
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

           result)))))
