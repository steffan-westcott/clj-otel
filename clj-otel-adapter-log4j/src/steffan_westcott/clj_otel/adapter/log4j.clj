(ns steffan-westcott.clj-otel.adapter.log4j
  "Appender and context data provider for Log4j."
  (:require [steffan-westcott.clj-otel.api.baggage :as baggage]
            [steffan-westcott.clj-otel.api.logs.log-record :as log-record]
            [steffan-westcott.clj-otel.api.trace.span :as span]
            [steffan-westcott.clj-otel.context :as context]
            [steffan-westcott.clj-otel.instrumentation.api.config :as inst-config])
  (:import (io.opentelemetry.api.baggage BaggageEntry)
           (io.opentelemetry.api.logs Severity)
           (io.opentelemetry.api.trace Span SpanContext TraceFlags TraceState)
           (io.opentelemetry.semconv.incubating ThreadIncubatingAttributes)
           (java.util.concurrent TimeUnit)
           (org.apache.logging.log4j Level)
           (org.apache.logging.log4j.core LogEvent)
           (org.apache.logging.log4j.core.time Instant)
           (org.apache.logging.log4j.message MapMessage Message)
           (org.apache.logging.log4j.spi StandardLevel)
           (steffan_westcott.clj_otel.adapter.log4j CljOtelAppender)))

(def ^:private StandardLevel->Severity
  {StandardLevel/ALL   Severity/TRACE
   StandardLevel/TRACE Severity/TRACE
   StandardLevel/DEBUG Severity/DEBUG
   StandardLevel/INFO  Severity/INFO
   StandardLevel/WARN  Severity/WARN
   StandardLevel/ERROR Severity/ERROR
   StandardLevel/FATAL Severity/FATAL
   StandardLevel/OFF   Severity/UNDEFINED_SEVERITY_NUMBER})

(defn- trace-id-key
  []
  (inst-config/get-string "otel.instrumentation.common.logging.trace-id" "trace_id"))

(defn- span-id-key
  []
  (inst-config/get-string "otel.instrumentation.common.logging.span-id" "span_id"))

(defn- trace-flags-key
  []
  (inst-config/get-string "otel.instrumentation.common.logging.trace-flags" "trace_flags"))

(defn- add-baggage?
  []
  (inst-config/get-boolean "otel.instrumentation.log4j-context-data.add-baggage" false))

(defn- assoc-all!
  [m kvs]
  (reduce (fn [m [k v]]
            (assoc! m k v))
          m
          kvs))

(defn context-data
  "Returns a string map to add to Log4j context data. This includes trace id,
   span id, trace flags and baggage (if enabled). The data is taken from the
   span in the bound or current context."
  []
  (let [span-context (span/get-span-context)]
    (if (.isValid span-context)
      (let [cdata (transient {(trace-id-key)    (.getTraceId span-context)
                              (span-id-key)     (.getSpanId span-context)
                              (trace-flags-key) (.asHex (.getTraceFlags span-context))})
            cdata (reduce (fn [m [k ^BaggageEntry v]]
                            (assoc! m
                                    (str "baggage." k)
                                    (.getValue v)))
                          cdata
                          (when (add-baggage?)
                            (.asMap (baggage/get-baggage))))]
        (persistent! cdata))
      {})))

(defn- context-from-cdata
  "Returns a context containing a span with span context data stored in cdata.
   This is used in cases where `emit` may be evaluated in a different context
   to where the log record occurred i.e. asynchronously."
  [cdata]
  (let [trace-id    (get cdata (trace-id-key))
        span-id     (get cdata (span-id-key))
        trace-flags (get cdata (trace-flags-key))]
    (and trace-id
         span-id
         trace-flags
         (context/assoc-value (context/root)
                              (Span/wrap (SpanContext/create trace-id
                                                             span-id
                                                             (TraceFlags/fromHex trace-flags 0)
                                                             (TraceState/getDefault)))))))

(defn- get-logger-name
  [^LogEvent event]
  (let [logger-name (.getLoggerName event)]
    (if (seq logger-name)
      logger-name
      "ROOT")))

(defn- get-source
  [^LogEvent event]
  (when-some [elem (.getSource event)]
    (let [line (.getLineNumber elem)]
      {:file (.getFileName elem)
       :fn   (str (.getClassName elem) "." (.getMethodName elem))
       :line (when (> line 0)
               line)})))

(defn- get-timestamp
  [^LogEvent event]
  (when-some [^Instant instant (.getInstant event)]
    (let [nanos (+ (.toNanos TimeUnit/MILLISECONDS (.getEpochMillisecond instant))
                   (.getNanoOfMillisecond instant))]
      [nanos TimeUnit/NANOSECONDS])))

(defn- ->log-record
  [^CljOtelAppender appender ^LogEvent event]
  (let [logger-name      (get-logger-name event)
        cdata            (.toMap (.getContextData event))
        context          (context/dyn)
        context          (if (identical? (context/root) context)
                           (context-from-cdata cdata) ; cover asynchronous case
                           context)
        ^Message message (.getMessage event)
        map-message?     (and message (instance? MapMessage message))
        body             (when message
                           (if map-message?
                             (.getFormat ^MapMessage message)
                             (.getFormattedMessage message)))
        check-msg-attr?  (and map-message? (not (seq body)))
        body             (if check-msg-attr?
                           (.get ^MapMessage message "message")
                           body)
        ^Level level     (.getLevel event)
        attributes       (persistent!
                          (cond-> (transient {})
                            (.-experimentalAttrs appender) (assoc!
                                                            ThreadIncubatingAttributes/THREAD_NAME
                                                            (.getThreadName event)
                                                            ThreadIncubatingAttributes/THREAD_ID
                                                            (.getThreadId event))
                            (and map-message? (.-mapMessageAttrs appender))
                            (assoc-all! (->> (.getData ^MapMessage message)
                                             (remove (fn [[k _]]
                                                       (and check-msg-attr? (= k "message"))))
                                             (map (fn [[k v]] [(str "log4j.map_message." k)
                                                               (str v)]))))

                            (.-markerAttr appender) (assoc! "log4j.marker" (.getMarker event))
                            :always (assoc-all! (filter
                                                 (fn [[k _]]
                                                   (and (not (and (.-eventName appender)
                                                                  (= k "event.name")))
                                                        (or (.-allCdataAttrs appender)
                                                            (contains? (.-cdataAttrs appender) k))))
                                                 cdata))))]
    {:logger-name   logger-name
     :context       context
     :severity      (and level (StandardLevel->Severity (.getStandardLevel level)))
     :severity-text (and level (.name level))
     :body          body
     :attributes    attributes
     :exception     (.getThrown event)
     :thread        nil ; thread added as attributes instead
     :timestamp     (get-timestamp event)
     :source        (when (.-codeAttrs appender)
                      (get-source event))
     :event-name    (when (.-eventName appender)
                      (get cdata "event.name"))}))

(defn- emit
  [record]
  (log-record/emit (assoc record
                          :logger
                          (log-record/get-logger {:name       (:logger-name record)
                                                  :version    nil
                                                  :schema-url nil}))))

(defn append
  "Appends a `LogEvent` by emitting as a log record. If `CljOtelAppender`
   instances have been initialized, the log record is emitted immediately (but
   not necessarily exported). Otherwise, the log record is added to a queue of
   delayed emits."
  [^CljOtelAppender appender ^LogEvent event]
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
   instance has been initialized. LogEvents appended beforehand are added to
   a queue of delayed emits."
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
