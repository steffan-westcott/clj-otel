(ns steffan-westcott.clj-otel.api.metrics.updown-counter
  (:require [steffan-westcott.clj-otel.api.attributes :refer [->attributes]])
  (:import (io.opentelemetry.api GlobalOpenTelemetry)
           (io.opentelemetry.api.metrics DoubleUpDownCounter
                                         LongUpDownCounter
                                         Meter
                                         ObservableDoubleMeasurement
                                         ObservableDoubleUpDownCounter
                                         ObservableLongMeasurement
                                         ObservableLongUpDownCounter)
           java.util.function.Consumer))


(defn long-updown-counter
  "Create a otel updown-counter for long values"
  ^LongUpDownCounter
  ([^Meter meter name] (long-updown-counter meter name nil nil))
  ([^Meter meter name description unit]
   (-> meter
       (.upDownCounterBuilder name)
       (.setUnit unit)
       (.setDescription description)
       .build)))


(defn double-updown-counter
  "Create a otel updown-counter for double values"
  ^DoubleUpDownCounter
  ([^Meter meter name] (double-updown-counter meter name nil nil))
  ([^Meter meter name description unit]
   (-> meter
       (.upDownCounterBuilder name)
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


(defn dec-counter!
  "Decrement the value of a given counter
  If no value is supplied, defaults to dec by 1"
  ([counter] (inc-counter! counter 1 nil))
  ([counter n] (inc-counter! counter n nil))
  ([counter n attrs]
   (.add counter (* n -1) (->attributes attrs))))


(defn observable-long-updown-counter
  "Create an observable updown counter for long values
  The callback should be a 0-arity function which returns
  a long value"
  ^ObservableLongUpDownCounter
  ([^Meter meter name callback] (observable-long-updown-counter meter name callback nil nil))
  ([^Meter meter name callback description unit]
   (-> meter
       (.upDownCounterBuilder name)
       (.setUnit unit)
       (.setDescription description)
       (.buildWithCallback (reify
                            Consumer
                              (accept [_ result]
                                (.record ^ObservableLongMeasurement result (callback))))))))


(defn observable-double-updown-counter
  "Create an observable updown counter for double values
  The callback should be a 0-arity function which returns
  a double value"
  ^ObservableDoubleUpDownCounter
  ([^Meter meter name callback] (observable-double-updown-counter meter name callback nil nil))
  ([^Meter meter name callback description unit]
   (-> meter
       (.upDownCounterBuilder name)
       (.ofDoubles)
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

  (def dcounter
    (double-updown-counter meter "clj-otel.updown-counter.double"))

  (inc-counter! dcounter)

  (dec-counter! dcounter 5)

  (def lcounter
    (long-updown-counter meter "clj-otel.updown-counter.long"))

  (inc-counter! lcounter 5 {:source :https})

  (def olcounter
    (observable-long-updown-counter meter
                                    "clj-otel.observable-counter.double"
                                    (fn []
                                      (System/currentTimeMillis)))))
