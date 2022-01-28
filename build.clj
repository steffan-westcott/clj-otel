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

;; Later artifacts in this vector may depend on earlier artifacts
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
                    "tutorial/instrumented"])

(def project-paths (concat artifact-ids example-paths))

(defn group-artifact-id [artifact-id]
  (str group-id "/" artifact-id))

(def group-artifact-ids (map group-artifact-id artifact-ids))

(defn- jar* [opts artifact-id]
  (b/set-project-root! artifact-id)
  (-> opts
      (assoc :lib (symbol group-id artifact-id)
             :version version
             :src-pom "template/pom.xml")
      cb/clean
      cb/jar))

(defn- install* [opts artifact-id]
  (let [opts' (jar* opts artifact-id)]
    (println (str "Installing " (group-artifact-id artifact-id) "..."))
    (cb/install opts')))

(defn- deploy* [opts artifact-id]
  (let [opts' (install* opts artifact-id)]
    (println (str "Deploying " (group-artifact-id artifact-id) "..."))
    (cb/deploy opts')))

(defn install
  "Ensure all clj-otel-* library JAR files are built and installed in the local
  Maven repository. The libraries are processed in an order such that later
  libraries may depend on earlier ones."
  [opts]
  (doseq [artifact-id artifact-ids]
    (install* opts artifact-id)))

(defn deploy
  "Ensure all clj-otel-* library JAR files are built, installed in the local
  Maven repository and deployed to Clojars. The libraries are processed in an
  order such that later libraries may depend on earlier ones."
  [opts]
  (doseq [artifact-id artifact-ids]
    (deploy* opts artifact-id)))

(defn lint
  "Lint all clj-otel-* libraries, example applications and tutorial source
  files using clj-kondo."
  [opts]
  (let [src-paths (map #(str % "/src") project-paths)]
    (-> opts
        (assoc :command-args (concat ["clj-kondo" "--lint"] src-paths))
        b/process)))

(defn outdated
  "Check all clj-otel-* libraries, example applications and tutorials for
  outdated dependencies using antq. Dependencies on clj-otel-* libraries are
  not checked, as they are not available until after deployment."
  [opts]
  (-> opts
      (assoc :main-opts ["--directory" (str/join ":" project-paths)
                         "--skip" "pom"
                         "--exclude" (str/join ":" group-artifact-ids)])
      (cb/run-task [:antq])))
