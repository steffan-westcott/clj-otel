(ns steffan-westcott.clj-otel.instrumentation.api.config
  "Functions for reading configuration properties from system properties or
   environment variables. Mirrors same functionality as
   `io.opentelemetry.instrumentation.api.internal.ConfigPropertiesUtil`."
  (:require [clojure.string :as str])
  (:import (java.util Locale)))

(def ^{:arglists '([k] [k default])} get-string
  "Returns string value for property `k` or `default`."
  (memoize (fn
             ([k]
              (or (System/getProperty k)
                  (System/getenv (-> (.toUpperCase ^String k Locale/ROOT)
                                     (.replace \- \_)
                                     (.replace \. \_)))))
             ([k default] (or (get-string k) default)))))

(def ^{:arglists '([k default])} get-int
  "Returns int value for property `k` or `default`."
  (memoize (fn [k default]
             (or
              (try
                (some-> (get-string k)
                        Integer/parseInt)
                (catch NumberFormatException _))
              default))))

(def ^{:arglists '([k default])} get-boolean
  "Returns boolean value for property `k` or `default`."
  (memoize (fn [k default]
             (if-some [v (get-string k)]
               (Boolean/parseBoolean v)
               default))))

(def ^{:arglists '([k default])} get-list
  "Returns list of string values for property `k` or `default`."
  (memoize (fn [k default]
             (if-some [v (get-string k)]
               (->> (str/split v #",")
                    (map str/trim)
                    (remove (fn [^String s]
                              (.isEmpty s))))
               default))))
