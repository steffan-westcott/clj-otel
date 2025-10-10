(ns steffan-westcott.clj-otel.sdk.otel-sdk
  "Programmatic configuration of the OpenTelemetry SDK."
  (:require [steffan-westcott.clj-otel.api.otel :as otel]
            [steffan-westcott.clj-otel.sdk.logger-provider :as logger]
            [steffan-westcott.clj-otel.sdk.meter-provider :as meter]
            [steffan-westcott.clj-otel.sdk.propagators :as propagators]
            [steffan-westcott.clj-otel.sdk.resources :as res]
            [steffan-westcott.clj-otel.sdk.tracer-provider :as tracer])
  (:import (io.opentelemetry.sdk OpenTelemetrySdk)))

(defn close-otel-sdk!
  "Close the given `OpenTelemetrySdk` instance, or the default instance
   used by `clj-otel`."
  ([]
   (close-otel-sdk! (otel/get-default-otel!)))
  ([^OpenTelemetrySdk sdk]
   (.close sdk)))

(defn add-shutdown-hook!
  "Adds a JVM shutdown hook that closes the given `OpenTelemetrySdk` instance,
   or the current default instance used by `clj-otel`."
  ([]
   (add-shutdown-hook! (otel/get-default-otel!)))
  ([^OpenTelemetrySdk sdk]
   (let [^Runnable runnable #(close-otel-sdk! sdk)
         hook (Thread. runnable)]
     (.addShutdownHook (Runtime/getRuntime) hook))))

(defn init-otel-sdk!
  "Returns a configured `OpenTelemetrySdk` instance. `service-name` is the
   service name given to the resource emitting telemetry. Optionally also sets
   the configured SDK as the default `OpenTelemetry` instance used by `clj-otel`
   and Java OpenTelemetry and registers a shutdown hook to close it.

   Takes a nested option map as described in the following sections. Some
   options can take either an option map or an equivalent fully configured Java
   object.

   Top level option map

   | key                     | description |
   |-------------------------|-------------|
   |`:set-as-default`        | If true, sets the configured SDK instance as the default `OpenTelemetry` instance declared and used by `clj-otel` (default: `true`).
   |`:set-as-global`         | If true, sets the configured SDK instance as the global `OpenTelemetry` instance declared by Java OpenTelemetry (default: `false`).
   |`:register-shutdown-hook`| If true, registers a JVM shutdown hook to close the configured SDK instance (default: `true`).
   |`:resources`             | Collection of resources to merge with default SDK resource and `service-name` resource. Each resource in the collection is either a `Resource` instance or a map with keys `:attributes` (required) and `:schema-url` (optional). The merged resource describes the source of telemetry and is attached to emitted data (default: nil)
   |`:clock`                 | `Clock` instance used for all temporal needs (default: system clock).
   |`:propagators`           | Collection of `TextMapPropagator` instances used to inject and extract context information using HTTP headers (default: W3C Trace Context and W3C Baggage text map propagators).
   |`:tracer-provider`       | Option map (see below) to configure `SdkTracerProvider` instance (default: no tracer provider).
   |`:meter-provider`        | Option map (see below) to configure `SdkMeterProvider` instance (default: no meter provider).
   |`:logger-provider`       | Option map (see below) to configure `SdkLoggerProvider` instance (default: no logger provider).


   ====================================================


   `:tracer-provider` option map

   | key              | description |
   |------------------|-------------|
   |`:span-processors`| Collection of option maps (see table below) or `SpanProcessor` instances. Each member specifies a collection of span exporters and batching to apply to those exporters (default: nil).
   |`:span-limits`    | Option map (see table below), `SpanLimits`, `Supplier` or fn which returns span limits (default: same as `{}`).
   |`:sampler`        | Option map (see table below) or `Sampler` instance, specifies strategy for sampling spans (default: same as `{:parent-based {}}`).
   |`:id-generator`   | `IdGenerator` instance for generating ids for spans and traces (default: platform specific `IdGenerator`).

   `:span-processors` member option map

   | key                      | description |
   |--------------------------|-------------|
   |`:exporters`              | Collection of `SpanExporter` instances, used to export spans to processing and reporting backends.
   |`:export-unsampled-spans?`| If true, unsampled spans are exported (default: false).
   |`:batch?`                 | If true, batches spans for export. If false then export spans individually; generally meant for debug logging exporters only. All options below are ignored when `:batch` is false (default: `true`).
   |`:schedule-delay`         | Delay interval between consecutive batched exports. Value is either a `Duration` or a vector `[amount ^TimeUnit unit]` (default: 5000ms).
   |`:exporter-timeout`       | Maximum time a batched export will be allowed to run before being cancelled. Value is either a `Duration` or a vector `[amount ^TimeUnit unit]` (default: 30000ms).
   |`:max-queue-size`         | Maximum number of spans kept in queue before start dropping (default: 2048).
   |`:max-export-batch-size`  | Maximum batch size for every export, must be smaller or equal to `:max-queue-size` (default: 512).

   `:span-limits` option map

   | key                  | description |
   |----------------------|-------------|
   |`:max-attrs`          | Maximum number of attributes per span (default: 128).
   |`:max-events`         | Maximum number of events per span (default: 128).
   |`:max-links`          | Maximum number of links per span (default: 128).
   |`:max-attrs-per-event`| Maximum number of attributes per event (default: 128).
   |`:max-attrs-per-link` | Maximum number of attributes per link (default: 128).
   |`:max-attr-value-len` | Maximum length of string attribute values and each element of string array attribute values (default: no maximum).

   `:sampler` option map (one option only)

   | key           | description |
   |---------------|-------------|
   |`:always`      | With value `:on` always record and export all spans. With value `:off` drop all spans.
   |`:ratio`       | double in range [0.0, 1.0], describing the ratio of spans to be sampled.
   |`:parent-based`| Option map (see table below), describing sampling decisions based on the parent span.

   `:parent-based` option map

   | key                        | description |
   |----------------------------|-------------|
   |`:root`                     | Option map (see `:sampler` table above) or `Sampler` to use when no parent span is present (default: same as `{:always :on}`).
   |`:remote-parent-sampled`    | Option map (see `:sampler` table above) or `Sampler` to use when there is a remote parent that was sampled (default: same as `{:always :on}`).
   |`:remote-parent-not-sampled`| Option map (see `:sampler` table above) or `Sampler` to use when there is a remote parent that was not sampled (default: same as `{:always :off}`).
   |`:local-parent-sampled`     | Option map (see `:sampler` table above) or `Sampler` to use when there is a local parent that was sampled (default: same as `{:always :on}`).
   |`:local-parent-not-sampled` | Option map (see `:sampler` table above) or `Sampler` to use when there is a local parent that was not sampled (default: same as `{:always :off}`).


   ====================================================


   `:meter-provider` option map

   | key      | description |
   |----------|-------------|
   |`:readers`| Collection of option maps (see table below) for specifying metric readers (default: no readers).
   |`:views`  | Collection of option maps (see table below) for specifying views that affect exported metrics (default: no views).

   `:readers` member option map

   | key            | description |
   |----------------|-------------|
   |`:metric-reader`| A `MetricReader` instance. See `steffan-westcott.clj-otel.sdk.meter-provider/periodic-metric-reader` and `PrometheusHttpServer`.

   `:views` member option map

   | key                  | description |
   |----------------------|-------------|
   |`:instrument-selector`| Option map (see table below), describing instrument selection criteria.
   |`:view`               | Option map (see table below), describing view that configures how measurements are aggregated and exported as metrics.

   `:instrument-selector` option map

   | key               | description |
   |-------------------|-------------|
   |`:type`            | Type of instruments to match, one of `:counter`, `:up-down-counter`, `:histogram` or `:gauge` (optional).
   |`:async?`          | True if instruments to match take measurements asynchronously (required if `:type` is specified).
   |`:name`            | Name of instrument to match (optional).
   |`:unit`            | Unit of instruments to match (optional).
   |`:meter-name`      | Name of meter associated with instruments to match (optional).
   |`:meter-version`   | Version of meter associated with instruments to match (optional).
   |`:meter-schema-url`| Schema URL of meter associated with instruments to match (optional).

   `:view` option map

   | key               | description |
   |-------------------|-------------|
   |`:name`            | Name of resulting metric (default: matched instrument name).
   |`:description`     | String description of resulting metric (default: matched instrument description).
   |`:aggregation`     | Option map (see table below) describing a single aggregation to use (default: dependent on instrument type).
   |`:attribute-filter`| Function which takes a string attribute name, which returns truthy result if attribute should be included (default: all attributes included).

   `:aggregation` option map (one option only)

   | key                                  | description |
   |--------------------------------------|-------------|
   |`:drop`                               | (value ignored) Drops all measurements and does not export any metric.
   |`:sum`                                | (value ignored) Aggregates all measurements to a long or double sum.
   |`:last-value`                         | (value ignored) Records last value as a gauge.
   |`:explicit-bucket-histogram`          | Option map (see table below) specifying aggregation of measurements into histogram buckets.
   |`:base-2-exponential-bucket-histogram`| Option map (see table below) specifying aggregation of measurements into base-2 sized histogram buckets.

   `:explicit-bucket-histogram` option map

   | key                | description |
   |--------------------|-------------|
   |`:bucket-boundaries`| Ordered collection of inclusive upper bounds of histogram buckets (default: implementation defined default bucket boundaries).

   `:base-2-exponential-bucket-histogram` option map

   | key                | description |
   |--------------------|-------------|
   |`:max-buckets`      | Maximum number of positive and negative buckets (default: implementation defined)
   |`:max-scale`        | Maximum and initial scale (required if `:max-buckets` is specified).


   ====================================================


   `:logger-provider` option map

   | key                    | description |
   |------------------------|-------------|
   |`:log-record-processors`| Collection of option maps (see table below) or `LogRecordProcessor` instances. Each member specifies a collection of log record exporters and batching to apply to those exporters (default: nil).
   |`:log-limits`           | Option map (see table below), `Supplier` or fn which returns option map (default: nil).

   `:log-record-processors` option map

   | key                    | description |
   |------------------------|-------------|
   |`:exporters`            | Collection of `SpanExporter` instances, used to export spans to processing and reporting backends.
   |`:batch?`               | If true, batches spans for export. If false then export spans individually; generally meant for debug logging exporters only. All options other than `:exporters` are ignored when `:batch` is false (default: `true`).
   |`:schedule-delay`       | Delay interval between consecutive batched exports. Value is either a `Duration` or a vector `[amount ^TimeUnit unit]` (default: 1000ms).
   |`:exporter-timeout`     | Maximum time a batched export will be allowed to run before being cancelled. Value is either a `Duration` or a vector `[amount ^TimeUnit unit]` (default: 30000ms).
   |`:max-queue-size`       | Maximum number of spans kept in queue before start dropping (default: 2048).
   |`:max-export-batch-size`| Maximum batch size for every export, must be smaller or equal to `:max-queue-size` (default: 512).

   `:log-limits` option map

   | key                 | description |
   |---------------------|-------------|
   |`:max-attrs`         | Maximum number of attributes attached to the log record (default: 128).
   |`:max-attr-value-len`| Maximum length of string attribute values and each element of string array attribute values (default: no maximum)."
  ^OpenTelemetrySdk
  [service-name
   {:keys [set-as-default set-as-global register-shutdown-hook resources clock propagators
           tracer-provider meter-provider logger-provider]
    :or   {set-as-default         true
           set-as-global          false
           register-shutdown-hook true
           propagators            (propagators/default)}}]
  (let [resource       (res/merge-resources-with-default service-name resources)
        meter-provider (when meter-provider
                         (meter/sdk-meter-provider (cond-> (assoc meter-provider :resource resource)
                                                     clock (assoc :clock clock))))
        tracer-provider (when tracer-provider
                          (tracer/sdk-tracer-provider (cond-> (assoc tracer-provider
                                                                     :resource       resource
                                                                     :meter-provider meter-provider)
                                                        clock (assoc :clock clock))))
        logger-provider (when logger-provider
                          (logger/sdk-logger-provider (cond-> (assoc logger-provider
                                                                     :resource       resource
                                                                     :meter-provider meter-provider)
                                                        clock (assoc :clock clock))))
        builder        (doto (OpenTelemetrySdk/builder)
                         (.setPropagators (propagators/context-propagators propagators))
                         (.setMeterProvider meter-provider)
                         (.setTracerProvider tracer-provider)
                         (.setLoggerProvider logger-provider))
        sdk            (if set-as-global
                         (.buildAndRegisterGlobal builder)
                         (.build builder))]
    (when register-shutdown-hook
      (add-shutdown-hook! sdk))
    (when set-as-default
      (otel/set-default-otel! sdk))
    sdk))
