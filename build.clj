;!zprint {:style [:respect-nl]}

(ns build
  "Build scripts for clj-otel-* libraries, examples and tutorials.

For example, to lint all clj-otel-* libraries:

clojure -T:build lint

To see a description of all build scripts:

clojure -A:deps -T:build help/doc"
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.build.api :as b]
            [clojure.tools.deps :as t]
            [deps-deploy.deps-deploy :as dd])
  (:import (java.nio.file FileSystems)))

(def ^:private group-id
  "com.github.steffan-westcott")

(def ^:private version
  "0.2.4-SNAPSHOT")

;; Later artifacts in this vector may depend on earlier artifacts
(def ^:private artifact-ids
  ["clj-otel-api"
   "clj-otel-sdk"
   "clj-otel-contrib-aws-resources"
   "clj-otel-contrib-aws-xray-propagator"
   "clj-otel-sdk-extension-jaeger-remote-sampler"
   "clj-otel-extension-trace-propagators"
   "clj-otel-exporter-jaeger-grpc"
   "clj-otel-exporter-jaeger-thrift"
   "clj-otel-exporter-logging"
   "clj-otel-exporter-logging-otlp"
   "clj-otel-exporter-otlp"
   "clj-otel-exporter-prometheus"
   "clj-otel-exporter-zipkin"
   "clj-otel-instrumentation-resources"
   "clj-otel-instrumentation-runtime-telemetry-java8"
   "clj-otel-instrumentation-runtime-telemetry-java17"])

(def ^:private demo-project-paths
  ["examples/common/core-async.utils"
   "examples/common/interceptor.utils"
   "examples/common/log4j2.utils"
   "examples/factorial-app"
   "examples/microservices/auto-instrument/interceptor/planet-service"
   "examples/microservices/auto-instrument/interceptor/solar-system-service"
   "examples/microservices/auto-instrument/middleware/sentence-summary-service"
   "examples/microservices/auto-instrument/middleware/word-length-service"
   "examples/microservices/manual-instrument/interceptor/average-service"
   "examples/microservices/manual-instrument/interceptor/sum-service"
   "examples/microservices/manual-instrument/middleware/puzzle-service"
   "examples/microservices/manual-instrument/middleware/random-word-service"
   "examples/square-app"
   "tutorial/instrumented"])

(def ^:private project-paths
  (concat artifact-ids demo-project-paths))

(defn- group-artifact-id
  [artifact-id]
  (str group-id "/" artifact-id))

(def ^:private group-artifact-ids
  (map group-artifact-id artifact-ids))

(def ^:private snapshot?
  (str/ends-with? version "-SNAPSHOT"))

(defn- head-sha-1
  []
  (b/git-process {:git-args "rev-parse HEAD"}))

(defn- artifact-opts
  [artifact-id]
  {:artifact-id   artifact-id
   :basis         (b/create-basis {:aliases (if snapshot?
                                              [:snapshot]
                                              [:release])})
   :class-dir     "target/classes"
   :jar-file      (format "target/%s-%s.jar" artifact-id version)
   :lib           (symbol group-id artifact-id)
   :resource-dirs ["resources"]
   :scm           {:tag (head-sha-1)}
   :src-dirs      ["src"]
   :src-pom       "template/pom.xml"
   :target-dir    "target"
   :version       version})

(defn- glob-match
  "Returns a predicate which returns true if a single glob `pattern` matches a
  `Path` arg and false otherwise. If given several patterns, returns true if
  any match and false if none match."
  ([pattern]
   (let [matcher (.getPathMatcher (FileSystems/getDefault) (str "glob:" pattern))]
     #(.matches matcher %)))
  ([pattern & pats]
   (let [f (apply some-fn (map glob-match (cons pattern pats)))]
     #(boolean (f %)))))

(defn- file-match
  "Returns path strings of files in directory tree `root`, with relative paths
  filtered by `pred`.
  Example: Match all *.clj files in directory tree `src`
  `(file-match \"src\" (glob-match \"**.clj\"))`"
  [root pred]
  (let [root-file (io/file root)
        root-path (.toPath root-file)]
    (->> root-file
         file-seq
         (filter #(.isFile %))
         (filter #(pred (.relativize root-path (.toPath %))))
         (map #(str (.normalize (.toPath %)))))))

(defn- globs
  "Returns path strings of files in directory tree `root` with relative paths
  matching any of glob `patterns`."
  [root & patterns]
  (file-match root (apply glob-match patterns)))

(defn- checked-process
  [params]
  (let [result (b/process params)]
    (if (zero? (:exit result))
      result
      (throw (ex-info "Process returned non-zero exit code" (assoc result :params params))))))

(defn- run-task
  "Run a task based on aliases."
  [{:keys [main-opts]} aliases]
  (let [task     (str/join ", " (map name aliases))
        _ (println "Running task for:" task)
        basis    (b/create-basis {:aliases aliases})
        combined (t/combine-aliases basis aliases)
        command  (b/java-command
                  {:basis     basis
                   :java-opts (:jvm-opts combined)
                   :main      'clojure.main
                   :main-args (into (:main-opts combined) main-opts)})]
    (checked-process command)))

(defn- clean-artifact
  [{:keys [target-dir]}]
  (println "Cleaning target ...")
  (b/delete {:path target-dir}))

(defn- jar-artifact
  [{:keys [class-dir jar-file resource-dirs src-dirs]
    :as   opts}]
  (clean-artifact opts)
  (println "Writing pom.xml ...")
  (b/write-pom opts)
  (println "Building jar" jar-file "...")
  (b/copy-dir {:src-dirs   (into src-dirs resource-dirs)
               :target-dir class-dir})
  (b/jar opts))

(defn- install-artifact
  [{:keys [artifact-id]
    :as   opts}]
  (jar-artifact opts)
  (println "Installing" (group-artifact-id artifact-id) "...")
  (b/install opts))

(defn- deploy-artifact
  [{:keys [artifact-id jar-file]
    :as   opts}]
  (install-artifact opts)
  (println "Deploying" (group-artifact-id artifact-id) "...")
  (dd/deploy {:artifact  (b/resolve-path jar-file)
              :installer :remote
              :pom-file  (b/pom-path opts)}))

(defn- tag-release
  [tag]
  (println "Creating and pushing tag" tag)
  (checked-process {:command-args ["git" "tag" "-a" "-m" (str "Release " tag) tag]})
  (checked-process {:command-args ["git" "push" "origin" tag]}))

(defn clean
  "Delete all clj-otel-* build directories."
  [_]
  (doseq [artifact-id artifact-ids]
    (b/set-project-root! artifact-id)
    (clean-artifact (artifact-opts artifact-id))))

(defn install
  "Build all clj-otel-* library JAR files then install them in the local Maven
  repository. The libraries are processed in an order such that later libraries
  may depend on earlier ones."
  [_]
  (doseq [artifact-id artifact-ids]
    (b/set-project-root! artifact-id)
    (install-artifact (artifact-opts artifact-id))))

(defn deploy
  "Build all clj-otel-* library JAR files, install them in the local Maven
  repository and deploy them to Clojars. The libraries are processed in an order
  such that later libraries may depend on earlier ones.

  For non-SNAPSHOT versions, a git tag with the version name is created and
  pushed to the origin repository."
  [_]
  (doseq [artifact-id artifact-ids]
    (b/set-project-root! artifact-id)
    (deploy-artifact (artifact-opts artifact-id)))
  (when-not snapshot?
    (tag-release version)))

(defn lint
  "Lint all clj-otel-* libraries, example applications and tutorial source
  files using clj-kondo. Assumes a working installation of `clj-kondo`
  executable binary."
  [_]
  (let [src-paths (map #(str % "/src") project-paths)]
    (checked-process {:command-args (concat ["clj-kondo" "--lint" "build.clj"] src-paths)})))

(defn outdated
  "Check all clj-otel-* libraries, example applications and tutorials for
  outdated dependencies using antq. Dependencies on clj-otel-* libraries are
  not checked, as they are not available until after deployment."
  [_]
  (run-task {:main-opts
             ["--directory" (str/join ":" project-paths)
              "--skip" "pom"
              "--exclude" (str/join ":" group-artifact-ids)
              "--no-changes"]}
            [:antq]))

(defn fmt
  "Apply formatting to all *.clj and *.edn source files using zprint. Assumes a
  working installation of `zprint` executable binary."
  [_]
  (let [project-files (mapcat #(globs % "src/**.clj" "*.edn" "resources/**.edn") project-paths)
        other-files   (globs "." "*.clj" "*.edn" ".clj-kondo/**.edn" "doc/**.edn")
        files         (concat project-files other-files)
        config-url    (-> ".zprint.edn"
                          io/file
                          io/as-url
                          str)]
    (checked-process {:command-args (concat ["zprint" "--url-only" config-url "-fsw"] files)})))
