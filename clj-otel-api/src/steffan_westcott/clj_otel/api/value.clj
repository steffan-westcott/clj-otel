(ns steffan-westcott.clj-otel.api.value
  "Conversion protocols supporting `io.opentelemetry.api.common.Value`."
  (:import (clojure.lang Keyword Seqable)
           (io.opentelemetry.api.common KeyValue
                                        KeyValueList
                                        ValueArray
                                        ValueBoolean
                                        ValueBytes
                                        ValueDouble
                                        ValueEmpty
                                        ValueLong
                                        ValueString)
           (java.nio ByteBuffer)
           (java.util List Map)))

(defprotocol AsValue
  (wrap ^io.opentelemetry.api.common.Value [x]
   "Returns `io.opentelemetry.api.common.Value` instance containing value `x`,
    where `x` is nil or a string, keyword, boolean, long, double, byte array,
    map, or coll. `x` may have nested structure. Keywords and map keys are
    transformed to strings."))

(extend-protocol AsValue
 nil
   (wrap [_]
     (io.opentelemetry.api.common.Value/empty))
 io.opentelemetry.api.common.Value
   (wrap [x]
     x)
 String
   (wrap [x]
     (io.opentelemetry.api.common.Value/of x))
 Keyword
   (wrap [x]
     (io.opentelemetry.api.common.Value/of (name x)))
 Boolean
   (wrap [x]
     (io.opentelemetry.api.common.Value/of x))
 Long
   (wrap [x]
     (io.opentelemetry.api.common.Value/of x))
 Double
   (wrap [x]
     (io.opentelemetry.api.common.Value/of x))
 Map
   (wrap [x]
     (io.opentelemetry.api.common.Value/of ^"[Lio.opentelemetry.api.common.KeyValue;"
                                           (into-array KeyValue
                                                       (map (fn [[k v]]
                                                              (KeyValue/of (name k) (wrap v)))
                                                            x))))
 Seqable
   (wrap [xs]
     (io.opentelemetry.api.common.Value/of ^List (map wrap xs)))
 Object
   (wrap [x]
     (io.opentelemetry.api.common.Value/of (str x))))

;; Declared separately to work around older Clojure compiler issues
#_:clj-kondo/ignore
(extend-protocol AsValue
 (Class/forName "[B")
 (wrap [x]
   (io.opentelemetry.api.common.Value/of ^bytes x)))

(defprotocol Value
  (unwrap [^io.opentelemetry.api.common.Value v]
   "Returns value contained in v."))

(extend-protocol Value
 nil
   (unwrap [_]
     nil)
 ValueEmpty
   (unwrap [_]
     nil)
 ValueString
   (unwrap [v]
     (.getValue v))
 ValueBoolean
   (unwrap [v]
     (.getValue v))
 ValueLong
   (unwrap [v]
     (.getValue v))
 ValueDouble
   (unwrap [v]
     (.getValue v))
 ValueBytes
   (unwrap [v]
     (let [^ByteBuffer byte-buffer (.getValue v)
           arr (byte-array (.limit byte-buffer))]
       (.get byte-buffer 0 arr)
       arr))
 ValueArray
   (unwrap [v]
     (mapv unwrap (.getValue v)))
 KeyValueList
   (unwrap [v]
     (persistent! (reduce (fn [m ^KeyValue kv]
                            (assoc! m (.getKey kv) (unwrap (.getValue kv))))
                          (transient {})
                          (.getValue v))))
 Object
   (unwrap [v]
     v))
