(ns steffan-westcott.clj-otel.resource.resources
  "Provide `Resource` objects describing the local host and running process."
  (:import (io.opentelemetry.instrumentation.resources ContainerResource
                                                       HostIdResource
                                                       HostResource
                                                       OsResource
                                                       ProcessResource
                                                       ProcessRuntimeResource)
           (io.opentelemetry.sdk.resources Resource)))

(defn container-resource
  "Returns a `Resource` with information about the container being run on if
   any."
  ^Resource []
  (ContainerResource/get))

(defn host-resource
  "Returns a `Resource` with information about the current host."
  ^Resource []
  (HostResource/get))

(defn host-id-resource
  "Returns a `Resource` with information about the current host ID."
  ^Resource []
  (HostIdResource/get))

(defn os-resource
  "Returns a `Resource` with information about the current operating system."
  ^Resource []
  (OsResource/get))

(defn process-resource
  "Returns a `Resource` with information about the current running process."
  ^Resource []
  (ProcessResource/get))

(defn process-runtime-resource
  "Returns a `Resource` with information about the Java runtime."
  ^Resource []
  (ProcessRuntimeResource/get))