(ns steffan-westcott.clj-otel.sdk.meter-provider
  "Programmatic configuration of `SdkMeterProvider`, a component of the
   OpenTelemetry SDK. This namespace is for internal use only, except for
   the function `periodic-metric-reader`."
  (:require [steffan-westcott.clj-otel.util :as util])
  (:import (io.opentelemetry.sdk.metrics Aggregation
                                         InstrumentSelector
                                         InstrumentType
                                         SdkMeterProvider
                                         SdkMeterProviderBuilder
                                         View)
           (io.opentelemetry.sdk.metrics.export PeriodicMetricReader)))

(defn periodic-metric-reader
  "Returns a `PeriodicMetricReader`. Takes an option map as follows:

   | key              | description |
   |------------------|-------------|
   |`:metric-exporter`| `MetricExporter` instance (required).
   |`:interval`       | Interval between each export. Value is either a `Duration` or a vector `[amount ^TimeUnit unit]` (default: 60s).
   |`:executor`       | `ScheduledExecutorService` instance for executing exports (default: thread pool with one daemon thread)."
  ^PeriodicMetricReader [{:keys [metric-exporter interval executor]}]
  (let [builder (cond-> (PeriodicMetricReader/builder metric-exporter)
                  interval (.setInterval (util/duration interval))
                  executor (.setExecutor executor))]
    (.build builder)))

(defn- register-metric-readers
  ^SdkMeterProviderBuilder [builder readers]
  (reduce (fn [b {:keys [metric-reader]}]
            (.registerMetricReader ^SdkMeterProviderBuilder b metric-reader))
          builder
          readers))

(defn- map->aggregation
  [agg]
  (let [[type opts] (first agg)]
    (case type
      :drop       (Aggregation/drop)
      :sum        (Aggregation/sum)
      :last-value (Aggregation/lastValue)

      :explicit-bucket-histogram (let [{:keys [bucket-boundaries]} opts]
                                   (if bucket-boundaries
                                     (Aggregation/explicitBucketHistogram (vec bucket-boundaries))
                                     (Aggregation/explicitBucketHistogram)))

      :base-2-exponential-bucket-histogram
      (let [{:keys [max-buckets max-scale]} opts]
        (if (and max-buckets max-scale)
          (Aggregation/base2ExponentialBucketHistogram max-buckets max-scale)
          (Aggregation/base2ExponentialBucketHistogram)))

      (Aggregation/defaultAggregation))))

(defn- instrument-type
  [type async?]
  (case type
    :counter         (if async?
                       InstrumentType/OBSERVABLE_COUNTER
                       InstrumentType/COUNTER)
    :up-down-counter (if async?
                       InstrumentType/OBSERVABLE_UP_DOWN_COUNTER
                       InstrumentType/UP_DOWN_COUNTER)
    :histogram       InstrumentType/HISTOGRAM
    :gauge           InstrumentType/OBSERVABLE_GAUGE))

(defn- map->instrument-selector
  [{:keys [type async? name unit meter-name meter-version meter-schema-url]}]
  (let [builder (cond-> (InstrumentSelector/builder)
                  type          (.setType (instrument-type type async?))
                  name          (.setName name)
                  unit          (.setUnit unit)
                  meter-name    (.setMeterName meter-name)
                  meter-version (.setMeterVersion meter-version)
                  meter-schema-url (.setMeterSchemaUrl meter-schema-url))]
    (.build builder)))

(defn- map->view
  [{:keys [name description aggregation attribute-filter]}]
  (let [builder (cond-> (View/builder)
                  name             (.setName name)
                  description      (.setDescription description)
                  aggregation      (.setAggregation (map->aggregation aggregation))
                  attribute-filter (.setAttributeFilter (util/predicate attribute-filter)))]
    (.build builder)))

(defn- register-views
  ^SdkMeterProviderBuilder [builder views]
  (reduce (fn [b {:keys [instrument-selector view]}]
            (.registerView ^SdkMeterProviderBuilder b
                           (map->instrument-selector instrument-selector)
                           (map->view view)))
          builder
          views))

(defn ^:no-doc sdk-meter-provider
  "Internal function that returns a `SdkMeterProvider`. See namespace
   `steffan-westcott.clj-otel.sdk.otel-sdk`"
  ^SdkMeterProvider [{:keys [readers views resource clock]}]
  (let [builder (cond-> (SdkMeterProvider/builder)
                  readers  (register-metric-readers readers)
                  views    (register-views views)
                  resource (.setResource resource)
                  clock    (.setClock clock))]
    (.build builder)))
