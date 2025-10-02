(ns ^:no-doc steffan-westcott.clj-otel.sdk.tracer-provider
  "Programmatic configuration of `SdkTracerProvider`, a component of the
   OpenTelemetry SDK. This namespace is for internal use only."
  (:require [steffan-westcott.clj-otel.sdk.resources :as res]
            [steffan-westcott.clj-otel.util :as util])
  (:import
    (clojure.lang Fn)
    (java.util Map)
    (java.util.function Supplier)
    (io.opentelemetry.sdk.trace SdkTracerProvider SdkTracerProviderBuilder SpanLimits SpanProcessor)
    (io.opentelemetry.sdk.trace.export BatchSpanProcessor
                                       BatchSpanProcessorBuilder
                                       SimpleSpanProcessor
                                       SimpleSpanProcessorBuilder
                                       SpanExporter)
    (io.opentelemetry.sdk.trace.samplers Sampler)))

(defprotocol ^:private AsSpanLimits
  (as-SpanLimits ^SpanLimits [span-limits]))

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
  (as-SpanLimits-Supplier ^Supplier [supplier]))

(extend-protocol AsSpanLimitsSupplier
 Supplier
   (as-SpanLimits-Supplier [supplier]
     supplier)
 Fn
   (as-SpanLimits-Supplier [supplier]
     (util/supplier #(as-SpanLimits (supplier)))))

(defprotocol AsSampler
  (as-Sampler ^Sampler [sampler]
   "Coerce to `Sampler`. May be given a `:sampler` option map, see
   `steffan-westcott.clj-otel.sdk.otel-sdk/init-otel-sdk!`."))

(defn- map->ParentBasedSampler
  [{:keys [root remote-parent-sampled remote-parent-not-sampled local-parent-sampled
           local-parent-not-sampled]
    :or   {root (Sampler/alwaysOn)}}]
  (let [builder
        (cond-> (Sampler/parentBasedBuilder (as-Sampler root))
          remote-parent-sampled     (.setRemoteParentSampled (as-Sampler remote-parent-sampled))
          remote-parent-not-sampled (.setLocalParentNotSampled (as-Sampler
                                                                remote-parent-not-sampled))
          local-parent-sampled      (.setLocalParentSampled (as-Sampler local-parent-sampled))
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

(defn- set-span-limits
  ^SdkTracerProviderBuilder [^SdkTracerProviderBuilder builder span-limits]
  (if (satisfies? AsSpanLimits span-limits)
    (.setSpanLimits builder ^SpanLimits (as-SpanLimits span-limits))
    (.setSpanLimits builder ^Supplier (as-SpanLimits-Supplier span-limits))))

(defprotocol ^:private AsSpanProcessor
  (as-SpanProcessor ^SpanProcessor [span-processor meter-provider]))

(extend-protocol AsSpanProcessor
 SpanProcessor
   (as-SpanProcessor [span-processor _]
     span-processor)
 Map
   (as-SpanProcessor [m meter-provider]
     (let [{:keys [^Iterable exporters export-unsampled-spans? batch? schedule-delay
                   exporter-timeout max-queue-size max-export-batch-size]
            :or   {batch? true}}
           m

           composite-exporter (SpanExporter/composite exporters)]
       (if batch?
         (let [^BatchSpanProcessorBuilder builder
               (cond-> (BatchSpanProcessor/builder composite-exporter)
                 (some? export-unsampled-spans?) (.setExportUnsampledSpans
                                                  (boolean export-unsampled-spans?))
                 schedule-delay        (.setScheduleDelay (util/duration schedule-delay))
                 exporter-timeout      (.setExporterTimeout (util/duration exporter-timeout))
                 max-queue-size        (.setMaxQueueSize max-queue-size)
                 max-export-batch-size (.setMaxExportBatchSize max-export-batch-size)
                 meter-provider        (.setMeterProvider meter-provider))]
           (.build builder))
         (let [^SimpleSpanProcessorBuilder builder
               (cond-> (SimpleSpanProcessor/builder composite-exporter)
                 (some? export-unsampled-spans?) (.setExportUnsampledSpans
                                                  (boolean export-unsampled-spans?)))]
           (.build builder))))))

(defn- add-span-processors
  ^SdkTracerProviderBuilder [builder span-processors meter-provider]
  (reduce #(.addSpanProcessor ^SdkTracerProviderBuilder %1 (as-SpanProcessor %2 meter-provider))
          builder
          span-processors))

(defn sdk-tracer-provider
  "Internal function that returns a `SdkTracerProvider`.
   See namespace `steffan-westcott.clj-otel.sdk.otel-sdk`"
  ^SdkTracerProvider
  [{:keys [span-processors span-limits sampler resource id-generator clock meter-provider]}]
  (let [builder
        (cond-> (add-span-processors (SdkTracerProvider/builder) span-processors meter-provider)
          span-limits  (set-span-limits span-limits)
          sampler      (.setSampler (as-Sampler sampler))
          resource     (.setResource (res/as-Resource resource))
          id-generator (.setIdGenerator id-generator)
          clock        (.setClock clock))]
    (.build builder)))
