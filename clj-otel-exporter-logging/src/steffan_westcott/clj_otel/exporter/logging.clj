(ns steffan-westcott.clj-otel.exporter.logging
  "Exporters that log telemetry data using `java.util.logging`. Intended for
   debugging only."
  (:import (io.opentelemetry.exporter.logging LoggingMetricExporter
                                              LoggingSpanExporter
                                              SystemOutLogRecordExporter)
           (io.opentelemetry.sdk.metrics.data AggregationTemporality)))

(defn span-exporter
  "Returns a span exporter that logs every span using `java.util.logging`.
   Intended for debugging only."
  ^LoggingSpanExporter []
  (LoggingSpanExporter/create))

(defn metric-exporter
  "Returns a metric exporter that logs every metric using `java.util.logging`.
   Intended for debugging only. May take an option map as follows:

   | key                      | description |
   |--------------------------|-------------|
   |`:aggregation-temporality`| ^AggregationTemporality Time period over which metrics should be aggregated (default: `CUMULATIVE`)."
  (^LoggingMetricExporter []
   (LoggingMetricExporter/create))
  (^LoggingMetricExporter
   [{:keys [aggregation-temporality]
     :or   {aggregation-temporality AggregationTemporality/CUMULATIVE}}]
   (LoggingMetricExporter/create aggregation-temporality)))

(defn log-record-exporter
  "Returns a log record exporter that records to standard out. Intended for
   debugging only."
  ^SystemOutLogRecordExporter []
  (SystemOutLogRecordExporter/create))
