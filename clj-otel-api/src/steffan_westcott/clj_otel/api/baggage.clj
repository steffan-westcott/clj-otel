(ns steffan-westcott.clj-otel.api.baggage
  "Conversion and manipulation functions for
   `io.opentelemetry.api.baggage.Baggage` objects."
  (:require [steffan-westcott.clj-otel.context :as context])
  (:import (io.opentelemetry.api.baggage Baggage BaggageBuilder BaggageEntry BaggageEntryMetadata)))

(defn get-baggage
  "Gets the baggage from a given context, or the current context if none is
   given. If no baggage is found in the context, empty baggage is returned."
  ([]
   (Baggage/current))
  ([context]
   (Baggage/fromContext context)))

(defn assoc-baggage
  "Associates baggage with a context and returns the new context."
  [context baggage]
  (context/assoc-value context baggage))

(defn- BaggageEntry->value
  [^BaggageEntry entry]
  (let [entry-value    (.getValue entry)
        metadata       (.getMetadata entry)
        metadata-value (.getValue metadata)]
    (if (empty? metadata-value)
      entry-value
      [entry-value metadata-value])))

(defn- put-entry
  [^BaggageBuilder builder k v]
  (let [k' (name k)]
    (if (string? v)
      (.put builder k' v)
      (let [[value metadata] v]
        (.put builder k' value (BaggageEntryMetadata/create metadata))))))

(defn ->map
  "Converts a `Baggage` instance to a map. Each key of the returned map is a
   string. Each value in the map is either `value` or a vector
   `[value metadata]`, where `value` and `metadata` are strings."
  [^Baggage baggage]
  (into {}
        (map (fn [[k v]] [k (BaggageEntry->value v)]))
        (.asMap baggage)))

(defn ->baggage
  "Converts a map to a `Baggage` instance. Each key in the map is either a
   string or keyword. Each value in the map is either `value` or a vector
   `[value metadata]`, where `value` and `metadata` are strings."
  [m]
  (.build ^BaggageBuilder (reduce-kv put-entry (Baggage/builder) m)))
