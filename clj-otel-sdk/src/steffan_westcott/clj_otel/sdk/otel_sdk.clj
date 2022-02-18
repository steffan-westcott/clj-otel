(ns steffan-westcott.clj-otel.sdk.otel-sdk
  "Programmatic configuration of the OpenTelemetry SDK."
  (:require [steffan-westcott.clj-otel.api.attributes :as attr]
            [steffan-westcott.clj-otel.api.otel :as otel]
            [steffan-westcott.clj-otel.propagator.w3c-baggage :as w3c-baggage]
            [steffan-westcott.clj-otel.propagator.w3c-trace-context :as w3c-trace]
            [steffan-westcott.clj-otel.util :as util])
  (:import
   (clojure.lang Fn)
   (java.util Map)
   (java.util.function Supplier)
   (io.opentelemetry.context.propagation ContextPropagators TextMapPropagator)
   (io.opentelemetry.sdk OpenTelemetrySdk)
   (io.opentelemetry.sdk.resources Resource)
   (io.opentelemetry.sdk.trace SdkTracerProvider SdkTracerProviderBuilder SpanLimits SpanProcessor)
   (io.opentelemetry.sdk.trace.export BatchSpanProcessor SimpleSpanProcessor SpanExporter)
   (io.opentelemetry.sdk.trace.samplers Sampler)
   (io.opentelemetry.semconv.resource.attributes ResourceAttributes)))

(defprotocol ^:private AsResource
  (as-Resource [resource]))

(extend-protocol AsResource
 Resource
   (as-Resource [resource]
     resource)
 Map
   (as-Resource [{:keys [attributes schema-url]}]
     (Resource/create (attr/->attributes attributes) schema-url)))

(defn- merge-resources-with-default
  [service-name resources]
  (let [service-resource {:attributes {ResourceAttributes/SERVICE_NAME service-name}}
        resources'       (cons service-resource resources)]
    (reduce #(.merge ^Resource %1 (as-Resource %2)) (Resource/getDefault) resources')))

(defprotocol ^:private AsSpanLimits
  (as-SpanLimits [span-limits]))

(extend-protocol AsSpanLimits
 SpanLimits
   (as-SpanLimits [span-limits]
     span-limits)
 Map
   (as-SpanLimits [{:keys [max-attrs max-events max-links max-attrs-per-event max-attrs-per-link
                           max-attr-value-len]}]
     (let [builder (cond-> (SpanLimits/builder)
                     max-attrs          (.setMaxNumberOfAttributes max-attrs)
                     max-events         (.setMaxNumberOfEvents max-events)
                     max-links          (.setMaxNumberOfLinks max-links)
                     max-attrs-per-event (.setMaxNumberOfAttributesPerEvent max-attrs-per-event)
                     max-attrs-per-link (.setMaxNumberOfAttributesPerLink max-attrs-per-link)
                     max-attr-value-len (.setMaxAttributeValueLength max-attr-value-len))]
       (.build builder))))

(defprotocol ^:private AsSpanLimitsSupplier
  (as-SpanLimits-Supplier [supplier]))

(extend-protocol AsSpanLimitsSupplier
 Supplier
   (as-SpanLimits-Supplier [supplier]
     supplier)
 Fn
   (as-SpanLimits-Supplier [supplier]
     (reify
      Supplier
        (get [_]
          (as-SpanLimits (supplier))))))

(defprotocol ^:no-doc AsSampler
  (as-Sampler [sampler]
   "Coerce to `Sampler`. May be given a `:sampler` option map, see [[init-otel-sdk!]]."))

(defn- map->ParentBasedSampler
  [{:keys [root remote-parent-sampled remote-parent-not-sampled local-parent-sampled
           local-parent-not-sampled]
    :or   {root (Sampler/alwaysOn)}}]
  (let [builder (cond-> (Sampler/parentBasedBuilder (as-Sampler root))
                  remote-parent-sampled     (.setRemoteParentSampled (as-Sampler
                                                                      remote-parent-sampled))
                  remote-parent-not-sampled (.setLocalParentNotSampled (as-Sampler
                                                                        remote-parent-not-sampled))
                  local-parent-sampled      (.setLocalParentSampled (as-Sampler
                                                                     local-parent-sampled))
                  local-parent-not-sampled  (.setLocalParentNotSampled (as-Sampler
                                                                        local-parent-not-sampled)))]
    (.build builder)))

(extend-protocol AsSampler
 Sampler
   (as-Sampler [sampler]
     sampler)
 Map
   (as-Sampler [{:keys [always ratio parent-based]}]
     (cond always       (case always
                          :on  (Sampler/alwaysOn)
                          :off (Sampler/alwaysOff))
           ratio        (Sampler/traceIdRatioBased ratio)
           parent-based (map->ParentBasedSampler parent-based))))

(defprotocol ^:private AsSpanProcessor
  (as-SpanProcessor [span-processor]))

(extend-protocol AsSpanProcessor
 SpanProcessor
   (as-SpanProcessor [span-processor]
     span-processor)
 Map
   (as-SpanProcessor [span-processor]
     (let [{:keys [^Iterable exporters batch? schedule-delay exporter-timeout max-queue-size
                   max-export-batch-size]
            :or   {batch? true}}
           span-processor

           composite-exporter (SpanExporter/composite exporters)]
       (if batch?
         (let [builder (cond-> (BatchSpanProcessor/builder composite-exporter)
                         schedule-delay        (.setScheduleDelay (util/duration schedule-delay))
                         exporter-timeout      (.setExporterTimeout (util/duration
                                                                     exporter-timeout))
                         max-queue-size        (.setMaxQueueSize max-queue-size)
                         max-export-batch-size (.setMaxExportBatchSize max-export-batch-size))]
           (.build builder))
         (SimpleSpanProcessor/create composite-exporter)))))

(defn- ^SdkTracerProviderBuilder set-span-limits
  [^SdkTracerProviderBuilder builder span-limits]
  (if (satisfies? AsSpanLimits span-limits)
    (.setSpanLimits builder ^SpanLimits (as-SpanLimits span-limits))
    (.setSpanLimits builder ^Supplier (as-SpanLimits-Supplier span-limits))))

(defn- get-sdk-tracer-provider
  [{:keys [span-processors span-limits sampler resource id-generator clock]
    :or   {span-processors []}}]
  (let [^SdkTracerProviderBuilder bb (reduce #(.addSpanProcessor ^SdkTracerProviderBuilder %1
                                                                 (as-SpanProcessor %2))
                                             (SdkTracerProvider/builder)
                                             span-processors)
        builder (cond-> bb
                  span-limits  (set-span-limits span-limits)
                  sampler      (.setSampler (as-Sampler sampler))
                  resource     (.setResource (as-Resource resource))
                  id-generator (.setIdGenerator id-generator)
                  clock        (.setClock clock))]
    (.build builder)))

(defn- get-open-telemetry-sdk
  [{:keys [tracer-provider ^Iterable text-map-propagators]}]
  (let [propagators (ContextPropagators/create (TextMapPropagator/composite text-map-propagators))
        builder     (doto (OpenTelemetrySdk/builder)
                     (.setPropagators propagators)
                     (.setTracerProvider tracer-provider))]
    (.build builder)))

(def ^:private sdk-tracer-provider
  (atom nil))

(defn init-otel-sdk!
  "Configure an `OpenTelemetrySdk` instance and set as the global
  `OpenTelemetry` instance. `service-name` is the service name given to the
  resource emitting telemetry. This function may be evaluated once only.
  Attempts to evaluate this more than once will result in error.

  Takes a nested option map as described in the
  following sections. Some options can take either an option map or an
  equivalent fully configured Java object.

  Top level option map

  | key              | description |
  |------------------|-------------|
  |`:resources`      | Collection of resources to merge with default SDK resource and `service-name` resource. Each resource in the collection is either a `Resource` instance or a map with keys `:attributes` (required) and `:schema-url` (optional). The merged resource describes the source of telemetry and is attached to emitted data (default: nil)
  |`:tracer-provider`| Required options map (see below) to configure `SdkTracerProvider` instance.
  |`:propagators`    | Collection of `TextMapPropagator` instances used to inject and extract context information using HTTP headers (default: W3C Trace Context and W3C Baggage text map propagators).

  `:tracer-provider` option map

  | key              | description |
  |------------------|-------------|
  |`:span-processors`| Collection of option maps (see table below) or `SpanProcessor` instances. Each member specifies a collection of span exporters and batching to apply to those exporters (default: `[]`).
  |`:span-limits`    | Option map (see table below), `SpanLimits`, `Supplier` or fn which returns span limits (default: same as `{}`).
  |`:sampler`        | Option map (see table below) or `Sampler` instance, specifies strategy for sampling spans (default: same as `{:parent-based {}}`).
  |`:id-generator`   | `IdGenerator` instance for generating ids for spans and traces (default: platform specific `IdGenerator`).
  |`:clock`          | `Clock` instance used for time reading operations (default: system clock).

  `:span-processors` member option map

  | key                    | description |
  |------------------------|-------------|
  |`:exporters`            | Collection of `SpanExporter` instances, used to export spans to processing and reporting backends.
  |`:batch?`               | If true, batches spans for export. If false then export spans individually; generally meant for debug logging exporters only. All options other than `:exporters` are ignored when `:batch` is false (default: `true`).
  |`:schedule-delay`       | Delay interval between consecutive batched exports. Value is either a `Duration` or a vector `[amount ^TimeUnit unit]` (default: 5000ms).
  |`:exporter-timeout`     | Maximum time a batched export will be allowed to run before being cancelled. Value is either a `Duration` or a vector `[amount ^TimeUnit unit]` (default: 30000ms).
  |`:max-queue-size`       | Maximum number of spans kept in queue before start dropping (default: 2048).
  |`:max-export-batch-size`| Maximum batch size for every export, must be smaller or equal to `:max-queue-size` (default: 512).

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
  |`:local-parent-not-sampled` | Option map (see `:sampler` table above) or `Sampler` to use when there is a local parent that was not sampled (default: same as `{:always :off}`)."
  [service-name
   {:keys [resources tracer-provider propagators]
    :or   {propagators [(w3c-trace/w3c-trace-context-propagator)
                        (w3c-baggage/w3c-baggage-propagator)]}}]
  (let [resource (merge-resources-with-default service-name resources)
        tracer-provider' (get-sdk-tracer-provider (assoc tracer-provider :resource resource))
        sdk      (get-open-telemetry-sdk {:tracer-provider      tracer-provider'
                                          :text-map-propagators propagators})]
    (otel/set-global-otel! sdk)
    (reset! sdk-tracer-provider tracer-provider')))

(defn close-otel-sdk!
  "Shut down activities of `OpenTelemetrySdk` instance previously configured
  by [[init-otel-sdk!]]."
  []
  (when-let [^SdkTracerProvider tracer-provider @sdk-tracer-provider]
    (.close tracer-provider)))
