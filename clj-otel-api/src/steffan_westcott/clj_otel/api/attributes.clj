(ns steffan-westcott.clj-otel.api.attributes
  "Conversion functions between maps and
   `io.opentelemetry.api.common.Attributes` objects."
  (:require [steffan-westcott.clj-otel.util :as util])
  (:import (io.opentelemetry.api.common AttributeKey AttributeType Attributes AttributesBuilder)))

(def ^:private type->keyfn
  {AttributeType/BOOLEAN       #(AttributeKey/booleanKey %)
   AttributeType/LONG          #(AttributeKey/longKey %)
   AttributeType/DOUBLE        #(AttributeKey/doubleKey %)
   AttributeType/STRING        #(AttributeKey/stringKey %)
   AttributeType/BOOLEAN_ARRAY #(AttributeKey/booleanArrayKey %)
   AttributeType/LONG_ARRAY    #(AttributeKey/longArrayKey %)
   AttributeType/DOUBLE_ARRAY  #(AttributeKey/doubleArrayKey %)
   AttributeType/STRING_ARRAY  #(AttributeKey/stringArrayKey %)})

(def ^:private type->valfn
  {AttributeType/BOOLEAN       boolean
   AttributeType/LONG          long
   AttributeType/DOUBLE        double
   AttributeType/STRING        str
   AttributeType/BOOLEAN_ARRAY #(map boolean %)
   AttributeType/LONG_ARRAY    #(map long %)
   AttributeType/DOUBLE_ARRAY  #(map double %)
   AttributeType/STRING_ARRAY  #(map str %)})

(defn- attribute-type-of
  "Returns `AttributeType` inferred from type of `x`."
  ^AttributeType [x]
  (cond (map? x)     AttributeType/STRING
        (coll? x)    (cond (every? boolean? x) AttributeType/BOOLEAN_ARRAY
                           (every? integer? x) AttributeType/LONG_ARRAY
                           (every? number? x)  AttributeType/DOUBLE_ARRAY
                           :else               AttributeType/STRING_ARRAY)
        (boolean? x) AttributeType/BOOLEAN
        (integer? x) AttributeType/LONG
        (number? x)  AttributeType/DOUBLE
        :else        AttributeType/STRING))

(def attribute-name
  "Function that returns a namespace qualified attribute name. May be
   overridden using [[set-attribute-name-fn!]]."
  (memoize util/qualified-name))

(defn set-attribute-name-fn!
  "Sets function for setting attribute names. See default function
   `steffan-westcott.clj-otel.util/qualified-name`."
  [f]
  (alter-var-root #'attribute-name (constantly f)))

(def ^:private attribute-key
  "Function that returns an `AttributeKey` for an attribute with the given type
   and key name."
  (memoize (fn [attribute-type k]
             ((type->keyfn attribute-type) k))))

(defn- attribute-value
  "Coerce `v` to a value of the given attribute type."
  [attribute-type v]
  ((type->valfn attribute-type) v))

(defn- attribute-key-value
  "Coerce `[k v]` to an `AttributeKey` and value pair. If `k` is not an
   `AttributeKey` instance, the attribute type is inferred from the type of
   `v`. In all cases `v` is coerced to an appropriate attribute type, or `nil`
   is returned if `v` is `nil`."
  [[k v]]
  (when (some? v)
    (let [AttributeKey? (instance? AttributeKey k)
          attribute-type (if AttributeKey?
                           (.getType ^AttributeKey k)
                           (attribute-type-of v))
          k (if AttributeKey?
              k
              (attribute-key attribute-type (attribute-name k)))
          v (attribute-value attribute-type v)]
      [k v])))

(defn ->map
  "Converts an `Attributes` instance to an attribute map. Each key of the
   returned map is a string."
  [^Attributes attributes]
  (into {}
        (map (fn [[^AttributeKey k v]] [(.getKey k) v]))
        (.asMap attributes)))

(defn ->attributes
  "Converts an attribute map to a `Attributes` instance. Each map key may be a
   keyword, string or `AttributeKey` instance. Each map value may be a boolean,
   long, double, string or a collection of one of those types. Attributes with
   `nil` values are dropped."
  ^Attributes [m]
  (let [kvs (keep attribute-key-value m)]
    (if (seq kvs)
      (let [builder (reduce (fn [^AttributesBuilder builder [^AttributeKey k v]]
                              (.put builder k v))
                            (Attributes/builder)
                            kvs)]
        (.build ^AttributesBuilder builder))
      (Attributes/empty))))
