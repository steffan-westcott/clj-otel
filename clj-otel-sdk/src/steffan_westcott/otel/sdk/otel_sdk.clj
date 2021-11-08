(ns steffan-westcott.otel.sdk.otel-sdk
  "Programmatic configuration of the OpenTelemetry SDK."
  (:require [steffan-westcott.otel.api.attributes :as attr]
            [steffan-westcott.otel.api.otel :as otel]
            [steffan-westcott.otel.propagator.w3c-baggage :as w3c-baggage]
            [steffan-westcott.otel.propagator.w3c-trace-context :as w3c-trace]
            [steffan-westcott.otel.util :as util])
  (:import (io.opentelemetry.sdk.trace SdkTracerProvider SpanLimits SpanProcessor SdkTracerProviderBuilder)
           (io.opentelemetry.context.propagation ContextPropagators TextMapPropagator)
           (io.opentelemetry.sdk.resources Resource)
           (io.opentelemetry.sdk.trace.samplers Sampler)
           (io.opentelemetry.sdk.trace.export BatchSpanProcessor SpanExporter SimpleSpanProcessor)
           (io.opentelemetry.sdk OpenTelemetrySdk)
           (java.util.function Supplier)))

(defn- as-Resource
  [resource]
  (if (instance? Resource resource)
    resource
    (let [{:keys [attributes schema-url]} resource]
      (Resource/create (attr/map->Attributes attributes) schema-url))))

(defn- merge-resources-with-default [resources]
  (reduce #(.merge ^Resource %1 (as-Resource %2)) (Resource/getDefault) resources))

(defn- ^SpanLimits as-SpanLimits
  [span-limits]
  (cond

    (instance? SpanLimits span-limits)
    span-limits

    (map? span-limits)
    (let [{:keys [max-attributes max-events max-links max-attrs-per-event max-attrs-per-link max-attr-value-len]} span-limits
          builder (cond-> (SpanLimits/builder)
                          max-attributes (.setMaxNumberOfAttributes max-attributes)
                          max-events (.setMaxNumberOfEvents max-events)
                          max-links (.setMaxNumberOfLinks max-links)
                          max-attrs-per-event (.setMaxNumberOfAttributesPerEvent max-attrs-per-event)
                          max-attrs-per-link (.setMaxNumberOfAttributesPerLink max-attrs-per-link)
                          max-attr-value-len (.setMaxAttributeValueLength max-attr-value-len))]
      (.build builder))))

(declare as-Sampler)

(defn- map->ParentBasedSampler
  [{:keys [root remote-parent-sampled remote-parent-not-sampled local-parent-sampled local-parent-not-sampled]
    :or   {root (Sampler/alwaysOn)}}]
  (let [builder (cond-> (Sampler/parentBasedBuilder (as-Sampler root))
                        remote-parent-sampled (.setRemoteParentSampled (as-Sampler remote-parent-sampled))
                        remote-parent-not-sampled (.setLocalParentNotSampled (as-Sampler remote-parent-not-sampled))
                        local-parent-sampled (.setLocalParentSampled (as-Sampler local-parent-sampled))
                        local-parent-not-sampled (.setLocalParentNotSampled (as-Sampler local-parent-not-sampled)))]
    (.build builder)))

(defn as-Sampler
  [sampler]
  (if (instance? Sampler sampler)
    sampler
    (let [{:keys [always ratio parent-based]} sampler]
      (cond
        always (case always
                 :on (Sampler/alwaysOn)
                 :off (Sampler/alwaysOff))
        ratio (Sampler/traceIdRatioBased ratio)
        parent-based (map->ParentBasedSampler parent-based)))))

(defn- as-SpanProcessor
  [span-processor]
  (if (instance? SpanProcessor span-processor)
    span-processor
    (let [{:keys [^Iterable exporters batch? schedule-delay exporter-timeout max-queue-size max-export-batch-size]
           :or   {batch? true}} span-processor
          composite-exporter (SpanExporter/composite exporters)]
      (if batch?
        (let [builder (cond-> (BatchSpanProcessor/builder composite-exporter)
                              schedule-delay (.setScheduleDelay (util/duration schedule-delay))
                              exporter-timeout (.setExporterTimeout (util/duration exporter-timeout))
                              max-queue-size (.setMaxQueueSize max-queue-size)
                              max-export-batch-size (.setMaxExportBatchSize max-export-batch-size))]
          (.build builder))
        (SimpleSpanProcessor/create composite-exporter)))))

(defn- ^SdkTracerProviderBuilder set-span-limits
  [^SdkTracerProviderBuilder builder span-limits]
  (if-let [^SpanLimits span-limits' (cond
                                      (instance? SpanLimits span-limits) span-limits
                                      (map? span-limits) (as-SpanLimits span-limits))]
    (.setSpanLimits builder span-limits')
    (if-let [^Supplier supplier (cond
                                  (instance? Supplier span-limits) span-limits
                                  (fn? span-limits) (reify Supplier
                                                      (get [_]
                                                        (as-SpanLimits (span-limits)))))]
      (.setSpanLimits builder supplier)
      builder)))

(defn- get-sdk-tracer-provider
  "Gets an [[SdkTracerProvider]] instance. Takes a nested option map as
  described in the sections below. Some options can take either an option map
  or an equivalent fully configured Java object.

  Top level option map

  | key              | description |
  |------------------|-------------|
  |`:span-processors`| Collection of option maps (see table below) or [[SpanProcessor]] instances, each member specifies span batching and exporting options.
  |`:span-limits`    | Option map (see table below), [[SpanLimits]], [[Supplier]] or fn which returns span limits (default: {})
  |`:sampler`        | Option map (see table below) or [[Sampler]] instance, specifies strategy for sampling spans.
  |`:resource`       | Option map or [[Resource]] instance (default: SDK default resource).
  |`:id-generator`   | [[IdGenerator]] instance for generating ids for spans and traces.
  |`:clock`          | [[Clock]] instance used for time reading operations (default: system clock)

  `:span-processors` member option map

  | key                    | description |
  |------------------------|-------------|
  |`:exporters`            | Collection of [[SpanExporter]] instances, used to export spans to processing and reporting backends.
  |`:batch?`               | If true, batches spans for export. If false then export spans individually; generally meant for logging exporters only. All options other than `:exporters` are ignored when `:batch` is false (default: `true`).
  |`:schedule-delay`       | Delay interval between consecutive batched exports. Value is either a [[Duration]] or a vector `[amount ^TimeUnit unit]` (default: 5000ms).
  |`:exporter-timeout`     | Maximum time a batched export will be allowed to run before being cancelled. Value is either a [[Duration]] or a vector `[amount ^TimeUnit unit]` (default: 30000ms).
  |`:max-queue-size`       | Maximum number of spans kept in queue before start dropping (default: 2048).
  |`:max-export-batch-size`| Maximum batch size for every export, must be smaller or equal to `:max-queue-size` (default: 512).

  `:span-limits` option map

  | key                  | description |
  |----------------------|-------------|
  |`:max-attributes`     | Maximum number of attributes per span (default: 128).
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
  |`:root`                     | Option map (see `:sampler` table above) or [[Sampler]] to use when no parent span is present (default: always on).
  |`:remote-parent-sampled`    | Option map (see `:sampler` table above) or [[Sampler]] to use when there is a remote parent that was sampled (default: always on).
  |`:remote-parent-not-sampled`| Option map (see `:sampler` table above) or [[Sampler]] to use when there is a remote parent that was not sampled (default: always off).
  |`:local-parent-sampled`     | Option map (see `:sampler` table above) or [[Sampler]] to use when there is a local parent that was sampled (default: always on).
  |`:local-parent-not-sampled` | Option map (see `:sampler` table above) or [[Sampler]] to use when there is a local parent that was not sampled (default: always off).

  `:resource` option map

  | key         | description |
  |-------------|-------------|
  |`:attributes`| Attribute map describing the entity for which traces/metrics are reported. Keys may be string, keyword or [[AttributeKey]].
  |`:schema-url`| URL of OpenTelemetry Schema used by this resource (default: nil)."
  [{:keys [span-processors span-limits sampler resource id-generator clock]
    :or   {span-processors []}}]
  (let [^SdkTracerProviderBuilder bb (reduce #(.addSpanProcessor ^SdkTracerProviderBuilder %1 (as-SpanProcessor %2))
                                             (SdkTracerProvider/builder)
                                             span-processors)
        builder (cond-> bb
                        span-limits (set-span-limits span-limits)
                        sampler (.setSampler (as-Sampler sampler))
                        resource (.setResource (as-Resource resource))
                        id-generator (.setIdGenerator id-generator)
                        clock (.setClock clock))]
    (.build builder)))

(defn- get-open-telemetry-sdk
  "Gets an [[OpenTelemetrySdk]] instance, which implements the
  [[OpenTelemetry]] API. Does not set this as the global [[OpenTelemetry]]
  instance. Takes a nested option map as described in the sections below. Some
  options can take either an option map or an equivalent fully configured Java
  object.

  | key                   | description |
  |-----------------------|-------------|
  |`:tracer-provider`     | [[SdkTracerProvider]] instance.
  |`:text-map-propagators`| Collection of [[TextMapPropagator]] instances to apply to spans (default: text map propagators for W3C Trace Context and W3C Baggage)."
  [{:keys [tracer-provider ^Iterable text-map-propagators]
    :or   {text-map-propagators [(w3c-trace/w3c-trace-context-propagator) (w3c-baggage/w3c-baggage-propagator)]}}]
  (let [propagators (ContextPropagators/create (TextMapPropagator/composite text-map-propagators))
        builder (cond-> (.setPropagators (OpenTelemetrySdk/builder) propagators)
                        tracer-provider (.setTracerProvider tracer-provider))]
    (.build builder)))

(def ^:private sdk-tracer-provider (atom nil))

(defn init-otel-sdk!
  "Configure a [[OpenTelemetrySdk]] instance and set as the global
  [[OpenTelemetry]] instance."
  [{:keys [resources tracer-provider-opts propagators]}]
  (let [resource (merge-resources-with-default resources)
        tracer-provider (get-sdk-tracer-provider (assoc tracer-provider-opts :resource resource))
        sdk-opts (cond-> {:tracer-provider tracer-provider}
                         propagators (assoc :text-map-propagators propagators))
        sdk (get-open-telemetry-sdk sdk-opts)]
    (otel/set-global-otel! sdk)
    (reset! sdk-tracer-provider tracer-provider)))

(defn close-otel-sdk!
  "Shut down activities of global [[OpenTelemetrySdk]] instance."
  []
  (when-let [^SdkTracerProvider tracer-provider @sdk-tracer-provider]
    (.close tracer-provider)))

(comment

  )
