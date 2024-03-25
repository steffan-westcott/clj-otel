(ns example.sum-service.app
  "Application logic. This is a simple application that sums collections of
   numbers."
  (:require [steffan-westcott.clj-otel.api.metrics.instrument :as instrument]
            [steffan-westcott.clj-otel.api.trace.span :as span]))


(defn sum
  "Return the sum of the nums."
  [{:keys [instruments]} nums]

  ;; Wrap synchronous function body with an internal span.
  (span/with-span! ["Calculating sum" {:system/nums nums}]

    (Thread/sleep 50)
    (let [result (reduce + 0 nums)]

      ;; Add an event to the internal span with some attributes attached.
      (span/add-event! "Computed sum" {:system/sum result})

      ;; Simulate an intermittent runtime exception when sum is 13.
      ;; An uncaught exception leaving a span's scope is reported as an
      ;; exception event and the span status description is set to the
      ;; exception triage summary.
      (when (= 13 result)
        (throw (RuntimeException. "Unlucky 13")))

      ;; Update sum-result metric
      (instrument/record! (:sum-result instruments) {:value result})

      result)))
