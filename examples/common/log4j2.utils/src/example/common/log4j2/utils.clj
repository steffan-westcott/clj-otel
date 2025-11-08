(ns example.common.log4j2.utils
  "Macros for Log4j2 logging with bound, current or explicit context."
  (:require [org.corfield.logging4j2.impl :as impl]
            [steffan-westcott.clj-otel.context :as context])
  (:import (io.opentelemetry.context Context)
           (org.apache.logging.log4j Level LogManager Logger)))

(defn log*
  "Internal fn to write a message to the log."
  [logger level context & more]
  (if (instance? Context context)
    (context/with-context! context
      (apply impl/log* logger level more))
    (apply impl/log* logger level context more)))

(defmacro log
  "Write a message to the log. If first of args is a context, use as explicit
   context for the message. Otherwise, use bound or current context."
  [level & args]
  `(let [^Logger logger# (LogManager/getLogger (str ~*ns*))
         ^Level level#   (get impl/levels ~level Level/ERROR)]
     (when (.isEnabled logger# level#)
       (log* logger# level# ~@args))))

(defmacro fatal
  "Write a FATAL message to the log. If first arg is a context, use as
  explicit context for the message. Otherwise, use bound or current context."
  [& args]
  `(log :fatal ~@args))

(defmacro error
  "Write an ERROR message to the log. If first arg is a context, use as
   explicit context for the message. Otherwise, use bound or current context."
  [& args]
  `(log :error ~@args))

(defmacro warn
  "Write a WARN message to the log. If first arg is a context, use as
   explicit context for the message. Otherwise, use bound or current context."
  [& args]
  `(log :warn ~@args))

(defmacro info
  "Write an INFO message to the log. If first arg is a context, use as
   explicit context for the message. Otherwise, use bound or current context."
  [& args]
  `(log :info ~@args))

(defmacro debug
  "Write a DEBUG message to the log. If first arg is a context, use as
   explicit context for the message. Otherwise, use bound or current context."
  [& args]
  `(log :debug ~@args))

(defmacro trace
  "Write a TRACE message to the log. If first arg is a context, use as
   explicit context for the message. Otherwise, use bound or current context."
  [& args]
  `(log :trace ~@args))
