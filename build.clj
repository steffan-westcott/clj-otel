(ns build
  "Build scripts for clj-otel-* libraries, examples and tutorials.

  To install all clj-otel-* libraries:

  clojure -T:build install

  To see a description of all build scripts:

  clojure -A:deps -T:build help/doc
  "
  (:require [clojure.tools.build.api :as b]
            [org.corfield.build :as cb]
            [clojure.string :as str]))

(def group-id "com.github.steffan-westcott")

(def version "0.1.0-SNAPSHOT")

(def artifact-ids ["clj-otel-api"
                   "clj-otel-sdk"
                   "clj-otel-sdk-extension-aws"
                   "clj-otel-sdk-extension-jaeger-remote-sampler"
                   "clj-otel-sdk-extension-resources"
                   "clj-otel-extension-aws"
                   "clj-otel-extension-trace-propagators"
                   "clj-otel-exporter-jaeger-grpc"
                   "clj-otel-exporter-jaeger-thrift"
                   "clj-otel-exporter-logging"
                   "clj-otel-exporter-logging-otlp"
                   "clj-otel-exporter-otlp-grpc-metrics"
                   "clj-otel-exporter-otlp-grpc-trace"
                   "clj-otel-exporter-otlp-http-metrics"
                   "clj-otel-exporter-otlp-http-trace"
                   "clj-otel-exporter-prometheus"
                   "clj-otel-exporter-zipkin"])

(def example-paths ["examples/auto-instrument-agent/interceptor/planet-service"
                    "examples/auto-instrument-agent/interceptor/solar-system-service"
                    "examples/auto-instrument-agent/middleware/sentence-summary-service"
                    "examples/auto-instrument-agent/middleware/word-length-service"
                    "examples/auto-sdk-config"
                    "examples/common-utils/core-async"
                    "examples/common-utils/interceptor"
                    "examples/common-utils/middleware"
                    "examples/manual-instrument/interceptor/average-service"
                    "examples/manual-instrument/interceptor/sum-service"
                    "examples/manual-instrument/middleware/puzzle-service"
                    "examples/manual-instrument/middleware/random-word-service"
                    "examples/programmatic-sdk-config"
                    "tutorial/instrumented"
                    "tutorial/uninstrumented"])

(def project-paths (concat artifact-ids example-paths))

(defn- jar+install [artifact-id opts]
  (println "\nProcessing artifact" artifact-id)
  (b/set-project-root! artifact-id)
  (-> opts
      (assoc :lib (symbol group-id artifact-id)
             :version version
             :src-pom "template/pom.xml")
      cb/clean
      cb/jar
      cb/install))

(defn install
  "Build all clj-otel-* library JAR files and install them in the local Maven
  repository."
  [opts]
  (doall (map #(jar+install % opts) artifact-ids)))

(defn lint
  "Lint all clj-otel-* libraries, example applications and tutorial source
  files using clj-kondo."
  [_opts]
  (let [src-paths (map #(str % "/src") project-paths)]
    (b/process {:command-args (concat ["clj-kondo" "--lint"] src-paths)})))

(defn outdated
  "Check all clj-otel-* libraries, example applications and tutorials for
  outdated dependencies using antq."
  [_opts]
  (cb/run-task {:main-opts ["--directory" (str/join ":" project-paths) "--skip" "pom"]}
               [:antq]))

(defn get-java-agent
  "Download OpenTelemetry instrumentation agent JAR to `examples` directory.
  This needs to be done once only before running any of the examples."
  [_opts]
  (let [basis (b/create-basis {:aliases [:java-agent]})
        src (get-in basis [:libs
                           'io.opentelemetry.javaagent/opentelemetry-javaagent
                           :paths
                           0])
        target "examples/opentelemetry-javaagent.jar"]
    (b/copy-file {:src src :target target})
    (println "Copied OpenTelemetry instrumentation agent JAR to" target)))
