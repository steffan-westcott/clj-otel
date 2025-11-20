(ns steffan-westcott.clj-otel.adapter.logback
  "Appenders providing log events and MDC for Logback. Has the same
   configuration and functionality provided by OpenTelemetry Java
   instrumentation versions, but is bound context aware."
  (:require [steffan-westcott.clj-otel.api.baggage :as baggage]
            [steffan-westcott.clj-otel.api.logs.log-record :as log-record]
            [steffan-westcott.clj-otel.api.trace.span :as span]
            [steffan-westcott.clj-otel.context :as context]
            [steffan-westcott.clj-otel.instrumentation.api.config :as inst-config]
            [steffan-westcott.clj-otel.instrumentation.api.config.logging :as config-logging]
            [steffan-westcott.clj-otel.util :as util])
  (:import (ch.qos.logback.classic.spi ILoggingEvent LoggerContextVO LoggingEvent ThrowableProxy)
           (io.opentelemetry.api.baggage BaggageEntry)
           (io.opentelemetry.api.logs Severity)
           (io.opentelemetry.api.trace Span SpanContext TraceFlags TraceState)
           (io.opentelemetry.semconv.incubating ThreadIncubatingAttributes)
           (java.lang.reflect Field)
           (java.util Map)
           (java.util.concurrent TimeUnit)
           (ch.qos.logback.classic Level)
           (org.slf4j Marker)
           (org.slf4j.event KeyValuePair)
           (steffan_westcott.clj_otel.adapter.logback CljOtelAppender)))

(def ^:private levelInt->Severity
  {Level/ALL_INT   Severity/TRACE
   Level/TRACE_INT Severity/TRACE
   Level/DEBUG_INT Severity/DEBUG
   Level/INFO_INT  Severity/INFO
   Level/WARN_INT  Severity/WARN
   Level/ERROR_INT Severity/ERROR})

(def ^:private supports-Instant?
  (util/has-method? ILoggingEvent "getInstant"))

(def ^:private supports-KeyValuePairs?
  (util/has-method? ILoggingEvent "getKeyValuePairs"))

(def ^:private supports-MarkerList?
  (util/has-method? ILoggingEvent "getMarkerList"))

(def ^:private supports-LogstashMarkers?
  (every? util/class-exists?
          ["net.logstash.logback.marker.LogstashMarker"
           "net.logstash.logback.marker.SingleFieldAppendingMarker"
           "net.logstash.logback.marker.MapEntriesAppendingMarker"]))

(def ^:private supports-logstash-StructuredArgument?
  (util/class-exists? "net.logstash.logback.argument.StructuredArgument"))

(def ^:private mdc-thread-id-key
  "clj-otel.adapter.logback.mdc.thread-id")

(def ^:private mdc-thread-name-key
  "clj-otel.adapter.logback.mdc.thread-name")

(defn- add-baggage?
  []
  (inst-config/get-boolean "otel.instrumentation.logback-mdc.add-baggage" false))

(defn- instance-LogStashMarker?
  [x]
  (util/resolve-instance? "net.logstash.logback.marker.LogstashMarker" x))

(defn- instance-SingleFieldAppendingMarker?
  [x]
  (util/resolve-instance? "net.logstash.logback.marker.SingleFieldAppendingMarker" x))

(defn- instance-MapEntriesAppendingMarker?
  [x]
  (util/resolve-instance? "net.logstash.logback.marker.MapEntriesAppendingMarker" x))

(defn assoc-context-data!
  "Add context data to MDC in mutable `LoggingEvent`. This includes trace id,
   span id, trace flags and baggage (if enabled). The data is taken from the
   span in the bound or current context."
  [^LoggingEvent event]
  (let [span-context (span/get-span-context)
        thread       (Thread/currentThread)
        mdc          (persistent!
                      (cond-> (util/into! (transient {mdc-thread-id-key   (.getId thread)
                                                      mdc-thread-name-key (.getName thread)})
                                          (.getMDCPropertyMap event))
                        (add-baggage?) (util/into! (map (fn [[k v]] [(str "baggage." k)
                                                                     (.getValue ^BaggageEntry v)]))
                                                   (.asMap (baggage/get-baggage)))
                        (.isValid span-context) (assoc! (config-logging/trace-id-key)
                                                        (.getTraceId span-context)
                                                        (config-logging/span-id-key)
                                                        (.getSpanId span-context)
                                                        (config-logging/trace-flags-key)
                                                        (.asHex (.getTraceFlags span-context)))))
        vo           (.getLoggerContextVO event)
        vo           (and vo (LoggerContextVO. (.getName vo) mdc (.getBirthTime vo)))]
    (.set ^Field (util/accessible-field LoggingEvent "mdcPropertyMap") event mdc)
    (.setLoggerContextRemoteView event vo)))

(defn- context-from-mdc
  "Returns a context containing a span with span context data stored in MDC.
   This is used in cases where `emit` may be evaluated in a different context
   to where the log record occurred i.e. asynchronously."
  [mdc]
  (let [trace-id    (get mdc (config-logging/trace-id-key))
        span-id     (get mdc (config-logging/span-id-key))
        trace-flags (get mdc (config-logging/trace-flags-key))]
    (and trace-id
         span-id
         trace-flags
         (context/assoc-value (context/root)
                              (Span/wrap (SpanContext/create trace-id
                                                             span-id
                                                             (TraceFlags/fromHex trace-flags 0)
                                                             (TraceState/getDefault)))))))

(defn- get-logger-name
  [^ILoggingEvent event]
  (let [logger-name (.getLoggerName event)]
    (if (seq logger-name)
      logger-name
      "ROOT")))

(defn- get-exception
  [^ILoggingEvent event]
  (when-some [e (.getThrowableProxy event)]
    (and (instance? ThrowableProxy e) (.getThrowable ^ThrowableProxy e))))

(defn- get-timestamp
  [^ILoggingEvent event]
  (or (and supports-Instant? (.getInstant event)) ;
      [(.getTimeStamp event) TimeUnit/MILLISECONDS]))

(defn- get-source
  [^ILoggingEvent event]
  (when-some [caller-data (.getCallerData event)]
    (when-some [^StackTraceElement elem (get caller-data 0)]
      (let [line (.getLineNumber elem)]
        {:file (.getFileName elem)
         :fn   (str (.getClassName elem) "." (.getMethodName elem))
         :line (when (> line 0)
                 line)}))))

(defn- flatten-markers
  "Given a collection of markers, returns the set of markers and all markers
   referenced by them, possibly recursively."
  [markers]
  (loop [res (transient #{})
         xs  (set markers)]
    (if-let [^Marker x (first xs)]
      (if (contains? res x)
        (recur res (disj xs x))
        (recur (conj! res x) (into (disj xs x) (iterator-seq (.iterator x)))))
      (persistent! res))))

(defn- logstash-single-field-attr
  [single-field-marker]
  (util/compile-when
   supports-LogstashMarkers?
   (when-let [^Field field (or (util/accessible-field (class single-field-marker) "fieldValue")
                               (util/accessible-field (class single-field-marker) "object")
                               (util/accessible-field (class single-field-marker) "rawJson"))]
     (let [k (.getFieldName ^net.logstash.logback.marker.SingleFieldAppendingMarker
                            single-field-marker)
           v (.get field single-field-marker)]
       [k v]))))

(defn- logstash-map-attrs
  [map-entries-marker]
  (util/compile-when supports-LogstashMarkers?
                     (let [^Field field (util/accessible-field (class map-entries-marker) "map")
                           m (and field (.get field map-entries-marker))]
                       (when (instance? Map m)
                         m))))

(defn- logstash-marker-attrs
  [markers]
  (persistent! (transduce (keep (fn [marker]
                                  (cond
                                    (instance-SingleFieldAppendingMarker? marker)
                                    (some->> (logstash-single-field-attr marker)
                                             (into {}))

                                    (instance-MapEntriesAppendingMarker? marker)
                                    (logstash-map-attrs marker))))
                          util/into!
                          (transient {})
                          markers)))

(defn- ->log-record
  [^CljOtelAppender appender ^ILoggingEvent event]
  (let [mdc (.getMDCPropertyMap event)
        context (context/dyn)
        context (if (identical? (context/root) context)
                  (context-from-mdc mdc) ; cover asynchronous case
                  context)
        level (.getLevel event)
        markers (flatten-markers (util/compile-if supports-MarkerList?
                                                  (.getMarkerList event)
                                                  (list (.getMarker event))))
        kv-pairs (util/compile-if supports-KeyValuePairs?
                                  (util/into! (transient {})
                                              (map (fn [^KeyValuePair kvp] [(.-key kvp)
                                                                            (.-value kvp)]))
                                              (when (.-captureKeyValuePairAttributes appender)
                                                (.getKeyValuePairs event)))
                                  (transient {}))
        attributes
        (persistent!
         (cond-> (util/into! kv-pairs
                             (filter (fn [[k _]]
                                       (or (.-captureAllMdcAttributes appender)
                                           (contains? (.-captureMdcAttributes appender) k))))
                             mdc)
           (.-captureExperimentalAttributes appender)
           (assoc! ThreadIncubatingAttributes/THREAD_ID
                   (get mdc mdc-thread-id-key)
                   ThreadIncubatingAttributes/THREAD_NAME
                   ;; Logback may synthesize thread name, so prefer name in MDC if present
                   (get mdc mdc-thread-name-key (.getThreadName event)))

           (.-captureMarkerAttribute appender)
           (assoc! "logback.marker"
                   (->> markers
                        (remove #(and (.-captureLogstashMarkerAttributes appender)
                                      (instance-LogStashMarker? %)))
                        (mapv #(.getName ^Marker %))))

           (.-captureLogstashMarkerAttributes appender) (util/into! (logstash-marker-attrs markers))
           (.-captureLoggerContext appender) (util/into! (.getPropertyMap (.getLoggerContextVO
                                                                           event)))
           (.-captureArguments appender)
           (assoc! "log.body.template"   (.getMessage event)
                   "log.body.parameters" (mapv str (.getArgumentArray event)))

           (and supports-logstash-StructuredArgument?
                (.-captureLogstashStructuredArguments appender))
           (util/into! (logstash-marker-attrs (.getArgumentArray event)))))

        event-name (when (.-captureEventName appender)
                     (get attributes "event.name"))
        attributes (cond-> attributes
                     event-name (dissoc "event.name"))]
    {:logger-name   (get-logger-name event)
     :context       context
     :severity      (and
                     level
                     (get levelInt->Severity (.levelInt level) Severity/UNDEFINED_SEVERITY_NUMBER))
     :severity-text (and level (.levelStr level))
     :body          (.getFormattedMessage event)
     :attributes    attributes
     :exception     (get-exception event)
     :thread        nil ; thread added as attributes instead
     :timestamp     (get-timestamp event)
     :source        (when (.-captureCodeAttributes appender)
                      (get-source event))
     :event-name    event-name}))

(defn- emit
  [record]
  (log-record/emit (assoc record
                          :logger
                          (log-record/get-logger {:name       (:logger-name record)
                                                  :version    nil
                                                  :schema-url nil}))))

(defn append
  "Appends a `ILoggingEvent` by emitting as a log record. If `CljOtelAppender`
   instances have been initialized, the log record is emitted immediately (but
   not necessarily exported). Otherwise, the log record is added to a queue of
   delayed emits."
  [^CljOtelAppender appender ^ILoggingEvent event]
  (let [record (->log-record appender event)]
    (if CljOtelAppender/initialized
      (emit record)
      (let [read-lock (.readLock CljOtelAppender/lock)]
        (.lock read-lock)
        (try
          (if CljOtelAppender/initialized
            (emit record)
            (.offer CljOtelAppender/delayedEmits record))
          (finally
            (.unlock read-lock)))))))

(defn initialize
  "Initializes all `CljOtelAppender` instances such that they emit records
   immediately on append. Causes queue of delayed emits to be drained.

   This fn should be evaluated as soon as the default or global OpenTelemetry
   instance has been initialized. LoggingEvents appended beforehand are added
   to a queue of delayed emits."
  []
  (let [write-lock (.writeLock CljOtelAppender/lock)]
    (.lock write-lock)
    (try
      (set! (. CljOtelAppender -initialized) true)
      (loop []
        (when-let [record (.poll CljOtelAppender/delayedEmits)]
          (emit record)
          (recur)))
      (finally
        (.unlock write-lock)))))
