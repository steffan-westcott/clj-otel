(ns steffan-westcott.clj-otel.exporter.logging
  "Exporters that log telemetry data using `java.util.logging`. Intended for
  debugging only."
  (:import (io.opentelemetry.exporter.logging LoggingMetricExporter LoggingSpanExporter)))

(defn span-exporter
  "Returns a span exporter that logs every span using `java.util.logging`."
  []
  (LoggingSpanExporter/create))

(defn metric-exporter
  "Returns a metric exporter that logs every metric using `java.util.logging`."
  []
  (LoggingMetricExporter/create))