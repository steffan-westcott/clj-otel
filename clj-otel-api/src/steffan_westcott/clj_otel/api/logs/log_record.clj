(ns steffan-westcott.clj-otel.api.logs.log-record
  "Functions for emitting log records.
   IMPORTANT: This namespace is for use by logging libraries only. To add
   logging to an application or general library, use a logging library that has
   OpenTelemetry log signal support instead e.g. Log4j2, Timbre."
  (:require [clojure.main :as main]
            [steffan-westcott.clj-otel.api.attributes :as attr]
            [steffan-westcott.clj-otel.api.otel :as otel]
            [steffan-westcott.clj-otel.api.value :as value]
            [steffan-westcott.clj-otel.config :refer [config]]
            [steffan-westcott.clj-otel.context :as context]
            [steffan-westcott.clj-otel.util :as util])
  (:import (clojure.lang IPersistentMap Keyword)
           (io.opentelemetry.api OpenTelemetry)
           (io.opentelemetry.api.logs LogRecordBuilder Logger Severity)
           (io.opentelemetry.semconv CodeAttributes ExceptionAttributes)
           (io.opentelemetry.semconv.incubating ThreadIncubatingAttributes)
           (java.io PrintWriter StringWriter)
           (java.util.concurrent ConcurrentLinkedQueue)
           (java.util.concurrent.locks ReadWriteLock ReentrantReadWriteLock)))

(def ^:private default-library
  (get-in config [:defaults :instrumentation-library]))

(defn get-logger
  "Builds and returns a `io.opentelemetry.api.logs.Logger` instance. May take
   an option map as follows:

   | key             | description |
   |-----------------|-------------|
   |`:name`          | Name of the *instrumentation* library, not the *instrumented* library e.g. `\"io.opentelemetry.contrib.mongodb\"` (default: See `config.edn` resource file).
   |`:version`       | Instrumentation library version e.g. `\"1.0.0\"` (default: See `config.edn` resource file).
   |`:schema-url`    | URL of OpenTelemetry schema used by this instrumentation library (default: See `config.edn` resource file).
   |`:open-telemetry`| `OpenTelemetry` instance to get logger from (default: default `OpenTelemetry` instance)."
  (^Logger []
   (get-logger {}))
  (^Logger
   [{:keys [name version schema-url open-telemetry]
     :or   {name       (:name default-library)
            version    (:version default-library)
            schema-url (:schema-url default-library)}}]
   (let [^OpenTelemetry otel (or open-telemetry (otel/get-default-otel!))
         builder (cond-> (.loggerBuilder (.getLogsBridge otel) name)
                   version    (.setInstrumentationVersion version)
                   schema-url (.setSchemaUrl schema-url))]
     (.build builder))))

(defn noop-logger
  "Gets a no-op logger."
  ^Logger []
  (get-logger {:open-telemetry (otel/get-noop)}))

(defonce ^:private default-logger
  (atom nil))

(defn set-default-logger!
  "Sets the default `io.opentelemetry.api.logs.Logger` instance used when
   creating log records. Returns `logger`. See also [[get-logger]]."
  ^Logger [logger]
  (reset! default-logger logger))

(defn- get-default-logger!
  "Returns the default logger if not nil. Otherwise, gets a logger using
   defaults and sets this as the default logger."
  ^Logger []
  (swap! default-logger #(or % (get-logger))))

(def ^:private keyword->Severity
  {:trace  Severity/TRACE
   :trace2 Severity/TRACE2
   :trace3 Severity/TRACE3
   :trace4 Severity/TRACE4
   :debug  Severity/DEBUG
   :debug2 Severity/DEBUG2
   :debug3 Severity/DEBUG3
   :debug4 Severity/DEBUG4
   :info   Severity/INFO
   :info2  Severity/INFO2
   :info3  Severity/INFO3
   :info4  Severity/INFO4
   :warn   Severity/WARN
   :warn2  Severity/WARN2
   :warn3  Severity/WARN3
   :warn4  Severity/WARN4
   :error  Severity/ERROR
   :error2 Severity/ERROR2
   :error3 Severity/ERROR3
   :error4 Severity/ERROR4
   :fatal  Severity/FATAL
   :fatal2 Severity/FATAL2
   :fatal3 Severity/FATAL3
   :fatal4 Severity/FATAL4})

(defprotocol ^:private AsSeverity
  (^:no-doc as-severity ^Severity [x]))

(extend-protocol AsSeverity
 nil
   (as-severity [_]
     Severity/UNDEFINED_SEVERITY_NUMBER)
 Severity
   (as-severity [x]
     x)
 Keyword
   (as-severity [x]
     (get keyword->Severity x Severity/UNDEFINED_SEVERITY_NUMBER)))

(defprotocol ^:private AsLogger
  (^:no-doc as-logger ^Logger [x]))

(extend-protocol AsLogger
 Logger
   (as-logger [logger]
     logger)
 String
   (as-logger [name]
     (get-logger {:name       name
                  :version    nil
                  :schema-url nil}))
 IPersistentMap
   (as-logger [opts]
     (get-logger opts)))

(defn- stacktrace
  [^Throwable e]
  (let [sw (StringWriter.)]
    (.printStackTrace e (PrintWriter. sw))
    (.toString sw)))

(defn- emit*
  "Emits a log record immediately. See [[emit]] for options."
  [{:keys [logger context severity severity-text body attributes ^Throwable exception ^Thread thread
           timestamp observed-timestamp event-name]
    {:keys [fn line col file]} :source}]
  (let [logger     (if logger
                     (as-logger logger)
                     (get-default-logger!))
        context    (or context (context/root))
        triage     (if exception
                     (into (assoc (main/ex-triage (Throwable->map exception))
                                  ExceptionAttributes/EXCEPTION_TYPE
                                  (.getCanonicalName (class exception))
                                  ExceptionAttributes/EXCEPTION_MESSAGE
                                  (.getMessage exception)
                                  ExceptionAttributes/EXCEPTION_STACKTRACE
                                  (stacktrace exception))
                           (ex-data exception))
                     {})
        attributes (into (cond-> (assoc triage
                                        CodeAttributes/CODE_FUNCTION_NAME
                                        fn
                                        CodeAttributes/CODE_LINE_NUMBER
                                        line
                                        CodeAttributes/CODE_COLUMN_NUMBER
                                        col
                                        CodeAttributes/CODE_FILE_PATH
                                        file)
                           thread (assoc ThreadIncubatingAttributes/THREAD_NAME
                                         (.getName thread)
                                         ThreadIncubatingAttributes/THREAD_ID
                                         (.getId thread)))
                         attributes)
        ^LogRecordBuilder builder
        (cond-> (.logRecordBuilder logger)
          :always            (.setContext context)
          severity           (.setSeverity (as-severity severity))
          severity-text      (.setSeverityText severity-text)
          body               (.setBody (value/wrap body))
          ;; TODO: Use ExtendedAttributes when API becomes available
          :always            (.setAllAttributes (attr/->attributes attributes))
          timestamp          (as-> b (let [[amount unit] (util/timestamp timestamp)]
                                       (.setTimestamp b amount unit)))
          observed-timestamp (as-> b (let [[amount unit] (util/timestamp timestamp)]
                                       (.setObservedTimestamp b amount unit)))
          event-name         (.setEventName event-name))]
    (.emit builder)))

(defonce ^:private initialized
  (volatile! false))

(defonce ^:private ^ReadWriteLock lock
  (ReentrantReadWriteLock.))

(defonce ^:private ^ConcurrentLinkedQueue delayed-emits
  (ConcurrentLinkedQueue.))

(defn emit
  "Emits a log record immediately or adds to a queue of delayed emits if not
   initialized. See also [[initialize]].

   IMPORTANT: This function is for use by logging libraries only. To add
   logging to an application or general library, use a logging library that has
   OpenTelemetry log signal support instead e.g. Log4j2, Timbre.

   Takes an option map as below. The defaults for `:context` and `:thread` may
   be suitable only if `emit` is evaluated synchronously when the log record
   occurs.

   | key                 | description |
   |---------------------|-------------|
   |`:logger`            | Either `io.opentelemetry.api.logs.Logger`, `get-logger` option map or logger name string (default: default logger, as set by [[set-default-logger!]]; if no default logger has been set, one will be set with default config).
   |`:context`           | Context of the log record. If `nil`, use the root context (default: bound or current context).
   |`:severity`          | `^io.opentelemetry.api.logs.Severity` or keyword `:traceN`, `:debugN`, `:infoN`, `:warnN`, `:errorN`, `:fatalN` where `N` is nothing or `2`, `3`, `4` (default: nil).
   |`:severity-text`     | Short name of log record severity (default: nil).
   |`:body`              | Body of log record; may be string, keyword, boolean, long, double, byte array, map or seqable coll. Body may have nested structure. Keywords and map keys are transformed to strings (default: nil).
   |`:attributes`        | Map of additional attributes for the log record (default: no attributes).
   |`:exception`         | Exception to attach to log record. Exception details are merged with the `:attributes` value (default: nil).
   |`:thread`            | Thread where the log record occurred, or `nil` for no thread. Thread details are merged with the `:attributes` value (default: current thread).
   |`:timestamp`         | Timestamp for when the log record occurred. Value is either an `Instant` or vector `[amount ^TimeUnit unit]` (default: nil).
   |`:observed-timestamp`| Timestamp for when the log record was observed by OpenTelemetry; may be later than `:timestamp` for asynchronous processing. Value is either an `Instant` or vector `[amount ^TimeUnit unit]` (default: current timestamp).
   |`:source`            | Map describing source code where log record occurred. Optional keys are `:fn`, `:line`, `:col` and `:file` (default: nil).
   |`:event-name`        | If not nil, a string event name. An event name identifies this log record as an event with specific structure of `:body` and `:attributes` (default: nil)."
  [{:keys [context thread]
    :or   {context (context/dyn)
           thread  (Thread/currentThread)}
    :as   opts}]
  (let [opts (assoc opts :context context :thread thread)]
    (if @initialized
      (emit* opts)
      (let [read-lock (.readLock lock)]
        (.lock read-lock)
        (try
          (if @initialized
            (emit* opts)
            (.offer delayed-emits opts))
          (finally
            (.unlock read-lock)))))))

(defn initialize
  "Process delayed emits, then ensure future emits are processed immediately.

   This fn should be evaluated as soon as the default or global OpenTelemetry
   instance has been initialized. Emits issued beforehand are added to a queue
   of delayed emits."
  []
  (let [write-lock (.writeLock lock)]
    (.lock write-lock)
    (try
      (vreset! initialized true)
      (loop []
        (when-let [record (.poll delayed-emits)]
          (emit* record)
          (recur)))
      (finally
        (.unlock write-lock)))))
