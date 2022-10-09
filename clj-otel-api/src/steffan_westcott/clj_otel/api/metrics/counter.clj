(ns steffan-westcott.clj-otel.api.metrics.counter
  (:require
    [steffan-westcott.clj-otel.api.attributes :refer [->attributes]])
  (:import
    (io.opentelemetry.api
      GlobalOpenTelemetry)
    (io.opentelemetry.api.metrics
      DoubleCounter
      LongCounter
      Meter
      ObservableDoubleCounter
      ObservableDoubleMeasurement
      ObservableLongCounter
      ObservableLongMeasurement)
    java.util.function.Consumer))


(defn long-counter
  "Create a otel counter for long values"
  ^LongCounter
  ([^Meter meter name]
   (long-counter meter name nil nil))
  ([^Meter meter name description unit]
   (-> meter
       (.counterBuilder name)
       (.setUnit unit)
       (.setDescription description)
       .build)))


(defn double-counter
  "Create a otel counter for double values"
  ^DoubleCounter
  ([^Meter meter name]
   (double-counter meter name nil nil))
  ([^Meter meter name description unit]
   (-> meter
       (.counterBuilder name)
       (.ofDoubles)
       (.setUnit unit)
       (.setDescription description)
       .build)))


(defn inc-counter!
  "Increment the value of a given counter
  If no value is supplied, defaults to inc by 1"
  ([counter] (inc-counter! counter 1 nil))
  ([counter n] (inc-counter! counter n nil))
  ([counter n attrs]
   (.add counter n (->attributes attrs))))


(defn observable-long-counter
  "Create an observable counter for long values
  The callback should be a 0-arity function which returns
  a long value"
  ^ObservableLongCounter
  ([^Meter meter name callback]
   (observable-long-counter meter name callback nil nil))
  ([^Meter meter name callback description unit]
   (-> meter
       (.counterBuilder name)
       (.setUnit unit)
       (.setDescription description)
       (.buildWithCallback (reify Consumer
                             (accept
                               [_ result]
                               (.record ^ObservableLongMeasurement result (callback))))))))


(defn observable-double-counter
  "Create an observable counter for double values
  The callback should be a 0-arity function which returns
  a double value"
  ^ObservableDoubleCounter
  ([^Meter meter name callback]
   (observable-double-counter meter name callback nil nil))
  ([^Meter meter name callback description unit]
   (-> meter
       (.counterBuilder name)
       (.ofDoubles)
       (.setUnit unit)
       (.setDescription description)
       (.buildWithCallback (reify Consumer
                             (accept
                               [_ result]
                               (.record ^ObservableDoubleMeasurement result (callback))))))))


;; ─────────────────────────────────── Usage ──────────────────────────────────

(comment
  (def meter (GlobalOpenTelemetry/getMeter "clj-otel.metrics.test"))

  (def dcounter (double-counter meter "clj-otel.counter.double" "test counter" "kgs"))

  (inc-counter! dcounter)

  (def lcounter (long-counter meter "clj-otel.counter.long"))

  (inc-counter! lcounter 5 {:source :https})


  (def odcounter
    (observable-double-counter meter
                               "clj-otel.observable-counter.double"
                               (fn [] (double (/ (System/currentTimeMillis) 1000)))))
  ,)
