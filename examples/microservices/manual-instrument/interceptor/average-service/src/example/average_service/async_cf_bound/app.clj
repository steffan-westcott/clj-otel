(ns example.average-service.async-cf-bound.app
  "Application logic, async CompletableFuture implementation using bound
   context."
  (:require [example.average-service.async-cf-bound.requests :as requests]
            [example.common.async.exec :as exec]
            [qbits.auspex :as aus]
            [steffan-westcott.clj-otel.api.metrics.instrument :as instrument]
            [steffan-westcott.clj-otel.api.trace.span :as span]))


(defn <divide
  "Divides x by y, returns a `CompletableFuture` of the result."
  [x y]
  (aus/future
    (bound-fn []

      ;; Wrap synchronous function body with an internal span.
      (span/with-bound-span! ["Calculating division" {:service.average.divide/parameters [x y]}]

        (Thread/sleep 10) ; pretend to be CPU intensive
        (let [result (double (/ x y))]

          ;; Add more attributes to internal span
          (span/add-span-data! {:attributes {:service.average.divide/result result}})

          result)))
    exec/cpu))



(defn <average
  "Calculate the average of the nums and return a `CompletableFuture` of the
   result."
  [components nums]

  ;; Wrap CompletableFuture with an asynchronous internal span.
  (span/async-bound-cf-span ["Calculating average" {:system/nums nums}]

                            (-> (requests/<get-sum components nums)
                                (aus/fmap (bound-fn [sum]
                                            (<divide sum (count nums)))))))



(defn <averages
  "Calculates the averages of the odd numbers and the even numbers of nums and
   returns a `CompletableFuture` of the result."
  [{:keys [instruments]
    :as   components} nums]
  (let [odds  (filter odd? nums)
        evens (filter even? nums)]
    (-> (aus/all [(when (seq odds)
                    (<average components odds))
                  (when (seq evens)
                    (<average components evens))])
        (aus/then
         (bound-fn [[odds-average evens-average]]
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

             result))))))
