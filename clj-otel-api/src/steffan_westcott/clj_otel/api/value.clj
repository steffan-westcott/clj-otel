(ns steffan-westcott.clj-otel.api.value
  "Conversion protocols supporting `io.opentelemetry.api.common.Value`."
  (:import (clojure.lang Keyword Seqable)
           (io.opentelemetry.api.common KeyValue
                                        KeyValueList
                                        ValueArray
                                        ValueBoolean
                                        ValueBytes
                                        ValueDouble
                                        ValueLong
                                        ValueString)
           (java.nio ByteBuffer)
           (java.util List Map)))

(defprotocol AsValue
  (value ^io.opentelemetry.api.common.Value [x]
   "Returns `io.opentelemetry.api.common.Value` instance containing x."))

(extend-protocol AsValue
 nil
   (value [_]
     nil)
 io.opentelemetry.api.common.Value
   (value [x]
     x)
 String
   (value [x]
     (io.opentelemetry.api.common.Value/of x))
 Keyword
   (value [x]
     (io.opentelemetry.api.common.Value/of (str x)))
 Boolean
   (value [x]
     (io.opentelemetry.api.common.Value/of x))
 Long
   (value [x]
     (io.opentelemetry.api.common.Value/of x))
 Double
   (value [x]
     (io.opentelemetry.api.common.Value/of x))
 Map
   (value [x]
     (io.opentelemetry.api.common.Value/of ^"[Lio.opentelemetry.api.common.KeyValue;"
                                           (into-array KeyValue
                                                       (map (fn [[k v]]
                                                              (KeyValue/of (str k) (value v)))
                                                            x))))
 Seqable
   (value [xs]
     (io.opentelemetry.api.common.Value/of ^List (map value xs)))
 Object
   (value [x]
     (io.opentelemetry.api.common.Value/of (str x))))

;; Declared separately to work around older Clojure compiler issues
#_:clj-kondo/ignore
(extend-protocol AsValue
 (Class/forName "[B")
 (value [x]
   (io.opentelemetry.api.common.Value/of ^bytes x)))

(defprotocol Value
  (get-value [^io.opentelemetry.api.common.Value x]
   "Returns value contained in x."))

(extend-protocol Value
 nil
   (get-value [_]
     nil)
 ValueString
   (get-value [x]
     (.getValue x))
 ValueBoolean
   (get-value [x]
     (.getValue x))
 ValueLong
   (get-value [x]
     (.getValue x))
 ValueDouble
   (get-value [x]
     (.getValue x))
 ValueBytes
   (get-value [x]
     (let [^ByteBuffer byte-buffer (.getValue x)
           arr (byte-array (.limit byte-buffer))]
       (.get byte-buffer 0 arr)
       arr))
 ValueArray
   (get-value [xs]
     (map get-value (.getValue xs)))
 KeyValueList
   (get-value [xs]
     (persistent! (reduce (fn [m ^KeyValue kv]
                            (assoc! m (.getKey kv) (get-value (.getValue kv))))
                          (transient {})
                          (.getValue xs)))))
