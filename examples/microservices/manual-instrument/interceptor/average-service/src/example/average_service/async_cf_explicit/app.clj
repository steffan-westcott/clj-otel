(ns example.average-service.async-cf-explicit.app
  "Application logic, async CompletableFuture implementation using explicit
   context."
  (:require [example.average-service.async-cf-explicit.requests :as requests]
            [example.common.async.exec :as exec]
            [example.common.log4j2.utils :as log]
            [qbits.auspex :as aus]
            [steffan-westcott.clj-otel.api.metrics.instrument :as instrument]
            [steffan-westcott.clj-otel.api.trace.span :as span]))


(defn <divide
  "Divides x by y."
  [context x y]
  (aus/future
    (fn []

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

          result)))
    exec/cpu))



(defn <average
  "Calculate the average of the nums and return a `CompletableFuture` of the
   result."
  [components context nums]

  ;; Wrap CompletableFuture with an asynchronous internal span. Context containing
  ;; internal span is assigned to `context*`.
  (span/cf-span-binding [context* {:parent     context
                                   :name       "Calculating average"
                                   :attributes {:system/nums nums}}]

    (-> (requests/<get-sum components context* nums)
        (aus/fmap (fn [sum]
                    (<divide context* sum (count nums)))))))



(defn <averages
  "Calculates the averages of the odd numbers and the even numbers of nums and
   returns a `CompletableFuture` of the result."
  [{:keys [instruments]
    :as   components} context nums]
  (let [odds  (filter odd? nums)
        evens (filter even? nums)]
    (-> (aus/all [(when (seq odds)
                    (<average components context odds))
                  (when (seq evens)
                    (<average components context evens))])
        (aus/then
         (fn [[odds-average evens-average]]
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

             result))))))


