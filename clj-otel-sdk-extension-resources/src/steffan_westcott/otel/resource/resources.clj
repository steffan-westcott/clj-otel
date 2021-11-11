(ns steffan-westcott.otel.resource.resources
  "Provide `Resource` objects describing the local host and running process."
  (:import (io.opentelemetry.sdk.extension.resources ContainerResource HostResource OsResource ProcessResource ProcessRuntimeResource)))

(defn container-resource
  "Returns a `Resource` with information about the container being run on if
  any."
  []
  (ContainerResource/get))

(defn host-resource
  "Returns a `Resource` with information about the current host."
  []
  (HostResource/get))

(defn os-resource
  "Returns a `Resource` with information about the current operating system."
  []
  (OsResource/get))

(defn process-resource
  "Returns a `Resource` with information about the current running process."
  []
  (ProcessResource/get))

(defn process-runtime-resource
  "Returns a `Resource` with information about the Java runtime."
  []
  (ProcessRuntimeResource/get))