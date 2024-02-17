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
            [deps-deploy.deps-deploy :as dd]
            [org.corfield.log4j2-conflict-handler :refer [log4j2-conflict-handler]])
  (:import (java.nio.file FileSystems)))

(def ^:private version
  "0.2.7-SNAPSHOT")

;; Later artifacts in this vector may depend on earlier artifacts
(def ^:private library-project-paths
  ["clj-otel-api"
   "clj-otel-sdk-common"
   "clj-otel-sdk"
   "clj-otel-contrib-aws-resources"
   "clj-otel-contrib-aws-xray-propagator"
   "clj-otel-sdk-extension-autoconfigure"
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

(def ^:private common-demo-project-paths
  ["examples/common/core-async.utils"
   "examples/common/interceptor.utils"
   "examples/common/load-gen"
   "examples/common/log4j2.utils"])

(def ^:private uber-demo-project-paths
  ["examples/microservices/auto-instrument/interceptor/planet-service"
   "examples/microservices/auto-instrument/interceptor/solar-system-load-gen"
   "examples/microservices/auto-instrument/interceptor/solar-system-service"
   "examples/microservices/auto-instrument/middleware/sentence-summary-load-gen"
   "examples/microservices/auto-instrument/middleware/sentence-summary-service"
   "examples/microservices/auto-instrument/middleware/word-length-service"
   "examples/microservices/manual-instrument/interceptor/average-load-gen"
   "examples/microservices/manual-instrument/interceptor/average-service"
   "examples/microservices/manual-instrument/interceptor/sum-service"
   "examples/microservices/manual-instrument/middleware/puzzle-load-gen"
   "examples/microservices/manual-instrument/middleware/puzzle-service"
   "examples/microservices/manual-instrument/middleware/random-word-service"])

(def ^:private other-demo-project-paths
  ["examples/countries-service"
   "examples/cube-app"
   "examples/divisor-app"
   "examples/factorial-app"
   "examples/square-app"
   "tutorial/instrumented"])

(defn- library-project?
  [root-path]
  (some #{root-path} library-project-paths))

(defn- uber-demo-project?
  [root-path]
  (some #{root-path} uber-demo-project-paths))

;; Used by examples/Dockerfile
#_{:clj-kondo/ignore [:unused-private-var]}
(def ^:private microservices-project-paths
  (concat common-demo-project-paths uber-demo-project-paths))

(def ^:private project-paths
  (concat library-project-paths
          common-demo-project-paths
          uber-demo-project-paths
          other-demo-project-paths))

(defn- group-id
  [root-path]
  (if (library-project? root-path)
    "com.github.steffan-westcott"
    "org.example"))

(defn- artifact-id
  [root-path]
  (re-find #"[^/]+$" root-path))

(defn- group-artifact-id
  [group-id artifact-id]
  (str group-id "/" artifact-id))

(def ^:private group-artifact-ids
  (map #(group-artifact-id (group-id %) (artifact-id %)) project-paths))

(def ^:private snapshot?
  (str/ends-with? version "-SNAPSHOT"))

(def ^:private head-sha-1
  (delay (b/git-process {:git-args "rev-parse HEAD"})))

(defn- artifact-opts
  [{:keys [aliases artifact-id group-id main root-path tag]}]
  {:artifact-id       artifact-id
   :basis             (b/create-basis {:aliases aliases})
   :class-dir         "target/classes"
   :conflict-handlers log4j2-conflict-handler
   :group-id          group-id
   :jar-file          (format "target/%s-%s.jar" artifact-id version)
   :lib               (symbol group-id artifact-id)
   :main              main
   :resource-dirs     ["resources"]
   :root-path         root-path
   :scm               {:tag tag}
   :src-dirs          ["src"]
   :src-pom           "template/pom.xml"
   :target-dir        "target"
   :uber-file         (format "target/%s-standalone.jar" artifact-id)
   :version           version})

(defn- project-artifact-opts
  [root-path]
  (artifact-opts {:artifact-id (artifact-id root-path)
                  :aliases     (cond
                                 (library-project? root-path) [(if snapshot?
                                                                 :snapshot
                                                                 :release)]
                                 (uber-demo-project? root-path)
                                 [:log4j])
                  :group-id    (group-id root-path)
                  :main        (when (uber-demo-project? root-path)
                                 (symbol (str "example." (artifact-id root-path))))
                  :root-path   root-path
                  :tag         (when (library-project? root-path)
                                 @head-sha-1)}))

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
  (println "Cleaning build dir" target-dir "...")
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
  [{:keys [group-id artifact-id]
    :as   opts}]
  (jar-artifact opts)
  (println "Installing" (group-artifact-id group-id artifact-id) "...")
  (b/install opts))

(defn- deploy-artifact
  [{:keys [group-id artifact-id jar-file]
    :as   opts}]
  (install-artifact opts)
  (println "Deploying" (group-artifact-id group-id artifact-id) "...")
  (dd/deploy {:artifact  (b/resolve-path jar-file)
              :installer :remote
              :pom-file  (b/pom-path opts)}))

(defn- uberjar-artifact
  [{:keys [class-dir resource-dirs root-path uber-file]
    :as   opts}]
  (clean-artifact opts)
  (b/copy-dir {:src-dirs   resource-dirs
               :target-dir class-dir})
  (println "Compiling project" root-path "...")
  (b/compile-clj opts)
  (println "Building uberjar" uber-file "...")
  (b/uber opts))

(defn- tag-release
  [tag]
  (println "Creating and pushing tag" tag)
  (checked-process {:command-args ["git" "tag" "-a" "-m" (str "Release " tag) tag]})
  (checked-process {:command-args ["git" "push" "origin" tag]}))

(defn clean
  "Delete all build directories."
  [_]
  (doseq [root-path project-paths]
    (b/with-project-root root-path
      (clean-artifact {:target-dir "target"}))))

(defn install
  "Build all clj-otel-* library JAR files then install them in the local Maven
  repository. The libraries are processed in an order such that later libraries
  may depend on earlier ones."
  ([_] (install _ library-project-paths))
  ([_ root-paths]
   (doseq [root-path root-paths]
     (b/with-project-root root-path
       (install-artifact (project-artifact-opts root-path))))))

(defn deploy
  "Build all clj-otel-* library JAR files, install them in the local Maven
  repository and deploy them to Clojars. The libraries are processed in an order
  such that later libraries may depend on earlier ones.

  For non-SNAPSHOT versions, a git tag with the version name is created and
  pushed to the origin repository."
  [_]
  (doseq [root-path library-project-paths]
    (b/with-project-root root-path
      (deploy-artifact (project-artifact-opts root-path))))
  (when-not snapshot?
    (tag-release version)))

(defn fetch-deps
  "Fetch dependencies for projects in collection referenced by symbol `paths`."
  [{:keys [paths]}]
  (doseq [root-path @(resolve paths)]
    (println "Fetching deps for" root-path "...")
    (b/with-project-root root-path
      (b/create-basis))))

(defn uberjar
  "Build an uberjar for the demo project with the given project name."
  [{:keys [project]}]
  (when-let [root-path (some #(and (= project (artifact-id %)) %) uber-demo-project-paths)]
    (b/with-project-root root-path
      (uberjar-artifact (project-artifact-opts root-path)))))

(defn lint
  "Lint all clj-otel-* libraries, example applications and tutorial source
  files using clj-kondo. Assumes a working installation of `clj-kondo`
  executable binary."
  [_]
  (let [src-paths (map #(str % "/src") project-paths)]
    (checked-process {:command-args (concat ["clj-kondo" "--lint" "build.clj"] src-paths)})))

(defn outdated
  "Check all clj-otel-* libraries, example applications and tutorials for
  outdated dependencies using antq. Dependencies on clj-otel-* projects are
  not checked."
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
