(ns steffan-westcott.otel.api.attributes
  "Conversion functions between maps and `Attributes` objects.

  Attributes may be attached to some OpenTelemetry objects such as spans and
  resources. Attribute keys are strings. Attribute values are booleans, longs,
  doubles, strings or a homogenous array of those types. Attributes with `nil`
  or empty values are dropped.

  OpenTelemetry has defined a rich standardised set of attributes describing
  vendor-agnostic telemetry data.
  See [OpenTelemetry semantic conventions documentation](https://opentelemetry.io/docs/concepts/glossary/#semantic-conventions)."
  (:require [steffan-westcott.otel.util :as util])
  (:import (io.opentelemetry.api.common Attributes AttributeKey AttributeType AttributesBuilder)))

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
  [x]
  (cond
    (map? x) AttributeType/STRING
    (coll? x) (cond
                (every? boolean? x) AttributeType/BOOLEAN_ARRAY
                (every? integer? x) AttributeType/LONG_ARRAY
                (every? number? x) AttributeType/DOUBLE_ARRAY
                :else AttributeType/STRING_ARRAY)
    (boolean? x) AttributeType/BOOLEAN
    (integer? x) AttributeType/LONG
    (number? x) AttributeType/DOUBLE
    :else AttributeType/STRING))

(def ^:private attribute-key
  "Function that returns a (memoized) `AttributeKey` for an attribute with
  the given type and key name."
  (memoize
    (fn [attribute-type k]
      ((get type->keyfn attribute-type) k))))

(defn- attribute-value
  "Coerce `v` to a value of the given attribute type."
  [attribute-type v]
  ((get type->valfn attribute-type) v))

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
          k' (if AttributeKey?
               k
               (attribute-key attribute-type (util/qualified-name k)))
          v' (attribute-value attribute-type v)]
      [k' v'])))

(defn ->map
  "Converts an `Attributes` instance to an attribute map. Each key of the
  returned map is a string."
  [^Attributes attributes]
  (into {}
        (map (fn [[^AttributeKey k v]] [(.getKey k) v]))
        (.asMap attributes)))

(defn map->Attributes
  "Converts an attribute map to a `Attributes` instance. Each map key may be a
  keyword, string or `AttributeKey` instance. Each map value may be a boolean,
  long, double, string or a homogenous array of those types. Attributes with
  `nil` or empty values are dropped."
  [m]
  (let [kvs (keep attribute-key-value m)]
    (if (seq kvs)
      (let [builder (reduce (fn [^AttributesBuilder builder [^AttributeKey k v]]
                              (.put builder k v))
                            (Attributes/builder)
                            kvs)]
        (.build ^AttributesBuilder builder))
      (Attributes/empty))))

(comment

  )