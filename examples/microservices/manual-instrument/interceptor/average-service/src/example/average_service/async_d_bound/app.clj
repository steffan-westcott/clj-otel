(ns example.average-service.async-d-bound.app
  "Application logic, Manifold implementation using bound context."
  (:require [example.average-service.async-d-bound.requests :as requests]
            [manifold.deferred :as d]
            [steffan-westcott.clj-otel.api.metrics.instrument :as instrument]
            [steffan-westcott.clj-otel.api.trace.d-span :as d-span]
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
  "Calculate the average of the nums and return a deferred of the result."
  [components nums]

  ;; Wrap deferred with an asynchronous internal span.
  (d-span/async-bound-d-span ["Calculating average" {:system/nums nums}]

                             (-> (requests/<get-sum components nums)
                                 (d/chain' (bound-fn [sum]
                                             (divide sum (count nums)))))))



(defn <averages
  "Calculates the averages of the odd numbers and the even numbers of nums and
   returns a deferred of the result."
  [{:keys [instruments]
    :as   components} nums]
  (let [odds  (filter odd? nums)
        evens (filter even? nums)]
    (-> (d/zip (when (seq odds)
                 (<average components odds))
               (when (seq evens)
                 (<average components evens)))
        (d/chain'
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
