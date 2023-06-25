(ns example.common.log4j2.utils
  "Functions for logging strings and maps using Log4j2."
  (:import (org.apache.logging.log4j Level LogBuilder LogManager)
           (org.apache.logging.log4j.message Message MapMessage)
           (org.apache.logging.log4j.spi LoggerContext)))

(def ^:private ^LoggerContext context
  (LogManager/getContext false))

(defn- ->MapMessage
  ^Message [m]
  (reduce (fn [^MapMessage mm [k v]]
            (.with mm (name k) v))
          (MapMessage. (count m))
          m))

#_{:clj-kondo/ignore [:missing-docstring]}
(defn log
  [logger-ns ^Level level x ^Throwable e]
  (let [logger (.getLogger context (str logger-ns))
        ^LogBuilder builder (cond-> (.atLevel logger level)
                              e (.withThrowable e))]
    (if (map? x)
      (.log builder (->MapMessage x))
      (.log builder x))))

(defmacro fatal
  "Log string or map `x` with optional exception `e` at `FATAL` severity."
  ([x] `(fatal ~x nil))
  ([x e] `(log ~*ns* Level/FATAL ~x ~e)))

(defmacro error
  "Log string or map `x` with optional exception `e` at `ERROR` severity."
  ([x] `(error ~x nil))
  ([x e] `(log ~*ns* Level/ERROR ~x ~e)))

(defmacro warn
  "Log string or map `x` with optional exception `e` at `WARN` severity."
  ([x] `(warn ~x nil))
  ([x e] `(log ~*ns* Level/WARN ~x ~e)))

(defmacro info
  "Log string or map `x` with optional exception `e` at `INFO` severity."
  ([x] `(info ~x nil))
  ([x e] `(log ~*ns* Level/INFO ~x ~e)))

(defmacro debug
  "Log string or map `x` with optional exception `e` at `DEBUG` severity."
  ([x] `(debug ~x nil))
  ([x e] `(log ~*ns* Level/DEBUG ~x ~e)))

(defmacro trace
  "Log string or map `x` with optional exception `e` at `TRACE` severity."
  ([x] `(trace ~x nil))
  ([x e] `(log ~*ns* Level/TRACE ~x ~e)))
