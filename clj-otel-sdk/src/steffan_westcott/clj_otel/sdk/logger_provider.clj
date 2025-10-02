(ns steffan-westcott.clj-otel.sdk.logger-provider
  "Programmatic configuration of `SdkLoggerProvider`, a component of the
   OpenTelemetry SDK. This namespace is for internal use only."
  (:require [steffan-westcott.clj-otel.sdk.resources :as res]
            [steffan-westcott.clj-otel.util :as util])
  (:import (clojure.lang IFn)
           (io.opentelemetry.sdk.logs LogLimits
                                      LogRecordProcessor
                                      SdkLoggerProvider
                                      SdkLoggerProviderBuilder)
           (io.opentelemetry.sdk.logs.export BatchLogRecordProcessor
                                             LogRecordExporter
                                             SimpleLogRecordProcessor)
           (java.util Map)
           (java.util.function Supplier)))

(defn- log-limits
  ^LogLimits [{:keys [max-attrs max-attr-value-len]}]
  (let [builder (cond-> (LogLimits/builder)
                  max-attrs (.setMaxNumberOfAttributes max-attrs)
                  max-attr-value-len (.setMaxAttributeValueLength max-attr-value-len))]
    (.build builder)))

(defprotocol ^:private AsLogLimitsSupplier
  (as-LogLimitsSupplier ^Supplier [x]))

(extend-protocol AsLogLimitsSupplier
 Supplier
   (as-LogLimitsSupplier [supplier]
     supplier)

 Map
   (as-LogLimitsSupplier [m]
     (util/supplier (constantly (log-limits m))))

 IFn
   (as-LogLimitsSupplier [f]
     (util/supplier #(log-limits (f)))))

(defprotocol ^:private AsLogRecordProcessor
  (as-LogRecordProcessor ^LogRecordProcessor [log-processor meter-provider]))

(extend-protocol AsLogRecordProcessor
 LogRecordProcessor
   (as-LogRecordProcessor [log-record-processor _]
     log-record-processor)
 Map
   (as-LogRecordProcessor [m meter-provider]
     (let [{:keys [^Iterable exporters batch? schedule-delay exporter-timeout max-queue-size
                   max-export-batch-size]
            :or   {batch? true}}
           m

           composite-exporter (LogRecordExporter/composite exporters)]
       (if batch?
         (let [builder (cond-> (BatchLogRecordProcessor/builder composite-exporter)
                         schedule-delay        (.setScheduleDelay (util/duration schedule-delay))
                         exporter-timeout      (.setExporterTimeout (util/duration
                                                                     exporter-timeout))
                         max-queue-size        (.setMaxQueueSize max-queue-size)
                         max-export-batch-size (.setMaxExportBatchSize max-export-batch-size)
                         meter-provider        (.setMeterProvider meter-provider))]
           (.build builder))
         (SimpleLogRecordProcessor/create composite-exporter)))))

(defn- add-log-processors
  ^SdkLoggerProviderBuilder [builder log-record-processors meter-provider]
  (reduce #(.addLogRecordProcessor ^SdkLoggerProviderBuilder %1
                                   (as-LogRecordProcessor %2 meter-provider))
          builder
          log-record-processors))

(defn sdk-logger-provider
  "Internal function that returns a `SdkLoggerProvider`.
   See namespace `steffan-westcott.clj-otel.sdk.otel-sdk`"
  ^SdkLoggerProvider
  [{:keys [log-record-processors log-limits resource clock meter-provider]}]
  (let [builder (cond-> (add-log-processors (SdkLoggerProvider/builder)
                                            log-record-processors
                                            meter-provider)
                  log-limits (.setLogLimits (as-LogLimitsSupplier log-limits))
                  resource   (.setResource (res/as-Resource resource))
                  clock      (.setClock clock))]
    (.build builder)))
