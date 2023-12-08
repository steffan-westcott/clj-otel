(ns ^:no-doc steffan-westcott.clj-otel.sdk.resources
  "Utilities for `io.opentelemetry.sdk.resources.Resource` objects."
  (:require [steffan-westcott.clj-otel.api.attributes :as attr])
  (:import (io.opentelemetry.semconv ResourceAttributes)
           (java.util Map)
           (io.opentelemetry.sdk.resources Resource)))

(defprotocol ^:private AsResource
  (^Resource as-Resource [resource] "Coerce to a `Resource` instance."))

(extend-protocol AsResource
 Resource
   (as-Resource [resource]
     resource)
 Map
   (as-Resource [{:keys [attributes schema-url]}]
     (Resource/create (attr/->attributes attributes) schema-url)))

(defn merge-resources-with-default
  "Given the service name and a collection of `Resource` instances, returns
   the merge of these with the default SDK resource as a single `Resource`
   object."
  ^Resource [service-name resources]
  (let [service-resource {:attributes {ResourceAttributes/SERVICE_NAME service-name}}
        resources'       (cons service-resource resources)]
    (reduce #(.merge ^Resource %1 (as-Resource %2)) (Resource/getDefault) resources')))
