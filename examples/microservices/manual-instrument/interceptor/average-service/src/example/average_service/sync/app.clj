(ns example.average-service.sync.app
  "Application logic, synchronous implementation."
  (:require [example.average-service.sync.requests :as requests]
            [steffan-westcott.clj-otel.api.metrics.instrument :as instrument]
            [steffan-westcott.clj-otel.api.trace.span :as span]))


(defn- divide
  "Divides x by y."
  [x y]

  ;; Wrap synchronous function body with an internal span.
  (span/with-span! ["Calculating division" {:service.average.divide/parameters [x y]}]

    (Thread/sleep 10)
    (let [result (double (/ x y))]

      ;; Add more attributes to internal span
      (span/add-span-data! {:attributes {:service.average.divide/result result}})

      result)))



(defn- average
  "Calculate the average of the nums."
  [components nums]

  ;; Wrap synchronous function body with an internal span.
  (span/with-span! ["Calculating average" {:system/nums nums}]

    (let [sum (requests/get-sum components nums)]
      (divide sum (count nums)))))



(defn averages
  "Calculates the averages of the odd numbers and the even numbers of nums and
   returns a channel of the result."
  [{:keys [instruments]
    :as   components} nums]
  (let [odds          (filter odd? nums)
        evens         (filter even? nums)
        odds-average  (when (seq odds)
                        (average components odds))
        evens-average (when (seq evens)
                        (average components evens))
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

    result))
