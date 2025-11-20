(ns example.common.slf4j.utils
  "Macros for SLF4J logging with bound, current or explicit context."
  (:require [steffan-westcott.clj-otel.context :as context]
            [steffan-westcott.clj-otel.util :as util])
  (:import (io.opentelemetry.context Context)
           (org.slf4j Logger LoggerFactory Marker)
           (org.slf4j.event Level)
           (org.slf4j.spi LoggingEventBuilder)))

(def ^:private levels
  {:trace Level/TRACE
   :debug Level/DEBUG
   :info  Level/INFO
   :warn  Level/WARN
   :error Level/ERROR})

(defn log*
  "Internal fn to use `logger` to log a `level` message specified by `more`.
   `more` is `context? marker* throwable? kvs? (message arg*)?` where `kvs` is a
   map with string keys and vals that are string or fn that returns a string,
   `message` is a string or fn that returns a string and each arg may be an
   object or fn that returns an object."
  [^Logger logger ^Level level & more]
  (let [builder (.makeLoggingEventBuilder logger level)
        [context more] (if (instance? Context (first more))
                         [(first more) (rest more)]
                         [nil more])
        [^LoggingEventBuilder builder more] (loop [^LoggingEventBuilder builder builder
                                                   more more]
                                              (if (instance? Marker (first more))
                                                (recur (.addMarker builder (first more))
                                                       (rest more))
                                                [builder more]))
        [^LoggingEventBuilder builder more] (if (instance? Throwable (first more))
                                              [(.setCause builder (first more)) (rest more)]
                                              [builder more])
        [^LoggingEventBuilder builder more] (if (map? (first more))
                                              [(reduce-kv
                                                (fn [^LoggingEventBuilder builder ^String k v]
                                                  (if (fn? v)
                                                    (.addKeyValue builder k (util/supplier v))
                                                    (.addKeyValue builder k v)))
                                                builder
                                                (first more)) (rest more)]
                                              [builder more])
        [message & args] more
        ^LoggingEventBuilder builder (if message
                                       (if (fn? message)
                                         (.setMessage builder (util/supplier message))
                                         (.setMessage builder (str message)))
                                       builder)
        ^LoggingEventBuilder builder (reduce (fn [^LoggingEventBuilder builder arg]
                                               (if (fn? arg)
                                                 (.addArgument builder (util/supplier arg))
                                                 (.addArgument builder arg)))
                                             builder
                                             args)]
    (if context
      (context/with-context! context
        (.log builder))
      (.log builder))))

(defmacro log'
  "Internal macro to log a message."
  [^Level level & args]
  `(let [^Logger logger# (LoggerFactory/getLogger (str ~*ns*))]
     (when (.isEnabledForLevel logger# ~level)
       (log* logger# ~level ~@args))))

(defmacro log
  "Logs a `level` message specified by `more`. `more` is `marker* throwable?
   kvs? (message arg*)?` where `kvs` is a map with string keys and vals that
   are string or fn that returns a string, `message` is a string or fn that
   returns a string and each arg may be an object or fn that returns an object."
  [level & args]
  `(log' (get levels ~level Level/ERROR) ~@args))

(defmacro error
  "Write an ERROR message to the log. If first arg is a context, use as
   explicit context for the message. Otherwise, use bound or current context."
  [& args]
  `(log' Level/ERROR ~@args))

(defmacro warn
  "Write a WARN message to the log. If first arg is a context, use as
   explicit context for the message. Otherwise, use bound or current context."
  [& args]
  `(log' Level/WARN ~@args))

(defmacro info
  "Write an INFO message to the log. If first arg is a context, use as
   explicit context for the message. Otherwise, use bound or current context."
  [& args]
  `(log' Level/INFO ~@args))

(defmacro debug
  "Write a DEBUG message to the log. If first arg is a context, use as
   explicit context for the message. Otherwise, use bound or current context."
  [& args]
  `(log' Level/DEBUG ~@args))

(defmacro trace
  "Write a TRACE message to the log. If first arg is a context, use as
   explicit context for the message. Otherwise, use bound or current context."
  [& args]
  `(log' Level/TRACE ~@args))
