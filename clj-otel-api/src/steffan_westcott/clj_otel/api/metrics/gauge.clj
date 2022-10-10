(ns steffan-westcott.clj-otel.api.metrics.gauge
  (:import (io.opentelemetry.api GlobalOpenTelemetry)
           (io.opentelemetry.api.metrics Meter
                                         ObservableDoubleGauge
                                         ObservableDoubleMeasurement
                                         ObservableLongGauge
                                         ObservableLongMeasurement)
           java.util.function.Consumer))


(defn long-gauge
  "Create an observable gauge for long values
  The callback should be a 0-arity function which returns
  a long value"
  ^ObservableLongGauge
  ([^Meter meter name callback] (long-gauge meter name callback nil nil))
  ([^Meter meter name callback description unit]
   (-> meter
       (.gaugeBuilder name)
       (.ofLongs)
       (.setUnit unit)
       (.setDescription description)
       (.buildWithCallback (reify
                            Consumer
                              (accept [_ result]
                                (.record ^ObservableLongMeasurement result (callback))))))))


(defn double-gauge
  "Create an observable gauge for double values
  The callback should be a 0-arity function which returns
  a double value"
  ^ObservableDoubleGauge
  ([^Meter meter name callback] (double-gauge meter name callback nil nil))
  ([^Meter meter name callback description unit]
   (-> meter
       (.gaugeBuilder name)
       (.setUnit unit)
       (.setDescription description)
       (.buildWithCallback (reify
                            Consumer
                              (accept [_ result]
                                (.record ^ObservableDoubleMeasurement result (callback))))))))


;; ─────────────────────────────────── Usage ──────────────────────────────────

(comment
  (def meter
    (GlobalOpenTelemetry/getMeter "clj-otel.metrics.test"))

  (def dgauge
    (long-gauge meter
                "clj-otel.gauge.long"
                (fn []
                  (System/currentTimeMillis))))

)
