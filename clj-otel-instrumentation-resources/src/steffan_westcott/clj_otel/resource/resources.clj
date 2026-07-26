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
  "Returns a `Resource` with information about the current running process. May
   take an option map as follows:

   | key                    | description |
   |------------------------|-------------|
   |`:include-command-attrs`| Include command line attributes, which may contain sensitive information (default: true)."
  (^Resource [] (process-resource {}))
  (^Resource
   [{:keys [include-command-attrs]
     :or   {include-command-attrs true}}]
   (ProcessResource/create (boolean include-command-attrs))))

(defn process-runtime-resource
  "Returns a `Resource` with information about the Java runtime."
  ^Resource []
  (ProcessRuntimeResource/get))