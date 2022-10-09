(ns steffan-westcott.clj-otel.api.metrics.histogram
  (:require
    [steffan-westcott.clj-otel.api.attributes :refer [->attributes]])
  (:import
    (io.opentelemetry.api
      GlobalOpenTelemetry)
    (io.opentelemetry.api.metrics
      DoubleHistogram
      LongHistogram
      Meter)))


(defn long-histogram
  "Create an otel histogram for long values"
  ^LongHistogram
  ([^Meter meter name]
   (long-histogram meter name nil nil))
  ([^Meter meter name description unit]
   (-> meter
       (.histogramBuilder name)
       (.setUnit unit)
       (.setDescription description)
       (.ofLongs)
       .build)))


(defn double-histogram
  "Create an otel histogram for double values"
  ^DoubleHistogram
  ([^Meter meter name]
   (double-histogram meter name nil nil))
  ([^Meter meter name description unit]
   (-> meter (.histogramBuilder name)
       (.setUnit unit)
       (.setDescription description)
       .build)))


(defn record-histogram!
  "Record a new value for given histogram"
  [histogram value attrs]
  (.record histogram value (->attributes attrs)))


;; ─────────────────────────────────── Usage ──────────────────────────────────

(comment
  (def meter (GlobalOpenTelemetry/getMeter "clj-otel.metrics.test"))

  (def dhist (double-histogram meter "clj-otel.histogram.double"))

  (record-histogram! dhist 5 {:status :success})
  ,)
