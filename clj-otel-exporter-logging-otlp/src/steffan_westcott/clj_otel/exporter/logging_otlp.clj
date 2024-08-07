(ns steffan-westcott.clj-otel.exporter.logging-otlp
  "Exporters that log telemetry data in OTLP JSON format using
   `java.util.logging`. Intended for debugging only."
  (:import (io.opentelemetry.exporter.logging.otlp OtlpJsonLoggingLogRecordExporter
                                                   OtlpJsonLoggingMetricExporter
                                                   OtlpJsonLoggingSpanExporter)
           (io.opentelemetry.sdk.logs.export LogRecordExporter)
           (io.opentelemetry.sdk.metrics.data AggregationTemporality)
           (io.opentelemetry.sdk.metrics.export MetricExporter)
           (io.opentelemetry.sdk.trace.export SpanExporter)))

(defn span-exporter
  "Returns a span exporter that logs every span in OTLP JSON format using
   `java.util.logging`. Intended for debugging only."
  ^SpanExporter []
  (OtlpJsonLoggingSpanExporter/create))

(defn metric-exporter
  "Returns a metric exporter that logs every metric in OTLP JSON format using
   `java.util.logging`. Intended for debugging only. May take an option map as
   follows:

   | key                      | description |
   |--------------------------|-------------|
   |`:aggregation-temporality`| ^AggregationTemporality Time period over which metrics should be aggregated (default: `CUMULATIVE`)."
  (^MetricExporter []
   (OtlpJsonLoggingMetricExporter/create))
  (^MetricExporter
   [{:keys [aggregation-temporality]
     :or   {aggregation-temporality AggregationTemporality/CUMULATIVE}}]
   (OtlpJsonLoggingMetricExporter/create aggregation-temporality)))

(defn log-record-exporter
  "Returns a log record exporter that logs records in OTLP JSON format using
   `java.util.logging`. Intended for debugging only."
  ^LogRecordExporter []
  (OtlpJsonLoggingLogRecordExporter/create))
