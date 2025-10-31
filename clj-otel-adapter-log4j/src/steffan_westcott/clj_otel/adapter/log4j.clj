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
           (org.apache.logging.log4j.spi StandardLevel)))

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
  (inst-config/get-string "clj-otel.instrumentation.common.logging.trace-id" "clj_trace_id"))

(defn- span-id-key
  []
  (inst-config/get-string "clj-otel.instrumentation.common.logging.span-id" "clj_span_id"))

(defn- trace-flags-key
  []
  (inst-config/get-string "clj-otel.instrumentation.common.logging.trace-flags" "clj_trace_flags"))

(defn- add-baggage?
  []
  (inst-config/get-boolean "clj-otel.instrumentation.log4j-context-data.add-baggage" false))

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
                                    (str "clj_baggage." k)
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

(defn- get-logger
  [^LogEvent event]
  (let [logger-name (.getLoggerName event)
        logger-name (if (seq logger-name)
                      logger-name
                      "ROOT")]
    (log-record/get-logger {:name       logger-name
                            :version    nil
                            :schema-url nil})))

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

(defn emit
  "Emits a log record containing data in ^LogEvent event, with options map as
   follows:

   | key                  | description |
   |----------------------|-------------|
   |`:code-attrs?`        | If true, include `:source` location where log record occurred (default: false).
   |`:experimental-attrs?`| If true, include thread data (default: false).
   |`:map-message-attrs?` | If true and event is a `MapMessage`, add content to log record attributes and set log record body to `message` value (default: false).
   |`:marker-attr?`       | If true, include Log4j marker as attribute (default: false).
   |`:all-cdata-attrs?`   | If true, include all Log4j context data as attributes (default: false).
   |`:cdata-attrs`        | Key set of Log4j context data to include as attributes, if `:all-cdata-attrs?` is false (default: no keys).
   |`:event-name?`        | If true, set log record event name as value of `event.name` in Log4j context data (default: false)."
  [^LogEvent event
   {:keys [code-attrs? experimental-attrs? map-message-attrs? marker-attr? all-cdata-attrs?
           cdata-attrs event-name?]}]
  (let [logger           (get-logger event)
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
                            experimental-attrs? (assoc! ThreadIncubatingAttributes/THREAD_NAME
                                                        (.getThreadName event)
                                                        ThreadIncubatingAttributes/THREAD_ID
                                                        (.getThreadId event))
                            (and map-message? map-message-attrs?)
                            (assoc-all! (->> (.getData ^MapMessage message)
                                             (remove (fn [[k _]]
                                                       (and check-msg-attr? (= k "message"))))
                                             (map (fn [[k v]] [(str "log4j.map_message." k)
                                                               (str v)]))))

                            marker-attr? (assoc! "log4j.marker" (.getMarker event))
                            :always (assoc-all! (filter (fn [[k _]]
                                                          (and (not (and event-name?
                                                                         (= k "event.name")))
                                                               (or all-cdata-attrs?
                                                                   (contains? cdata-attrs k))))
                                                        cdata))))]
    (log-record/emit {:logger        logger
                      :context       context
                      :severity      (and level (StandardLevel->Severity (.getStandardLevel level)))
                      :severity-text (and level (.name level))
                      :body          body
                      :attributes    attributes
                      :exception     (.getThrown event)
                      :thread        nil ; thread added as attributes instead
                      :timestamp     (get-timestamp event)
                      :source        (when code-attrs?
                                       (get-source event))
                      :event-name    (when event-name?
                                       (get cdata "event.name"))})))
