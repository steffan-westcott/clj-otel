;!zprint {:style [:respect-nl]}

(ns build
  "Build scripts for clj-otel-* libraries, examples and tutorials.

For example, to lint all clj-otel-* libraries:

clojure -T:build lint

To see a description of all build scripts:

clojure -A:deps -T:build help/doc"
  (:require [clojure.data.xml :as xml]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.build.api :as b]
            [clojure.tools.deps :as t]
            [clojure.zip :as zip]
            [deps-deploy.deps-deploy :as dd]
            [org.corfield.log4j2-conflict-handler :refer [log4j2-conflict-handler]])
  (:import (java.nio.file FileSystems)))

(def ^:private version
  "0.2.11-SNAPSHOT")

(def ^:private library-group-id
  "com.github.steffan-westcott")

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
   "clj-otel-exporter-logging"
   "clj-otel-exporter-logging-otlp"
   "clj-otel-exporter-otlp"
   "clj-otel-exporter-prometheus"
   "clj-otel-exporter-zipkin"
   "clj-otel-instrumentation-resources"
   "clj-otel-instrumentation-runtime-telemetry-java8"
   "clj-otel-instrumentation-runtime-telemetry-java17"
   "clj-otel-adapter-log4j"
   "clj-otel-adapter-logback"])

(def ^:private demo-project-paths
  ["examples/common/anonymise-app"
   "examples/common/async"
   "examples/common/load-gen"
   "examples/common/log4j2.utils"
   "examples/common/slf4j.utils"
   "examples/common/system"
   "examples/countries-service"
   "examples/cube-app"
   "examples/divisor-app"
   "examples/factorial-app"
   "examples/microservices/auto-instrument/interceptor/planet-service"
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
   "examples/microservices/manual-instrument/middleware/random-word-service"
   "examples/rpg-service"
   "examples/square-app"
   "examples/word-stream-app"
   "tutorial/instrumented"])

(xml/alias-uri 'pom "http://maven.apache.org/POM/4.0.0")

(defn- library-project?
  [root-path]
  (some #{root-path} library-project-paths))

(def ^:private project-paths
  (concat library-project-paths
          demo-project-paths))

(defn- group-id
  [root-path]
  (if (library-project? root-path)
    library-group-id
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
  [{:keys [aliases artifact-id group-id main root-path tag uber-classifier]}]
  {:artifact-id       artifact-id
   :basis             (b/create-basis {:aliases aliases})
   :basis-provided    (b/create-basis {:aliases (conj aliases :provided)})
   :class-dir         "target/classes"
   :conflict-handlers log4j2-conflict-handler
   :group-id          group-id
   :jar-file          (format "target/%s-%s.jar" artifact-id version)
   :java-src-dirs     ["java"]
   :javac-opts        ["--release" "8"]
   :lib               (symbol group-id artifact-id)
   :main              main
   :native-file       (format "target/%s" artifact-id)
   :resource-dirs     ["resources"]
   :root-path         root-path
   :scm               {:tag tag}
   :src-dirs          ["src"]
   :src-pom           "template/pom.xml"
   :target-dir        "target"
   :uber-file         (format "target/%s-%s.jar" artifact-id uber-classifier)
   :version           version})

(defn- modify-artifact-opts
  [{:keys [artifact-id]
    :as   opts}]
  (case artifact-id
    "clj-otel-adapter-log4j"
    (update
     opts
     :javac-opts
     into
     ["-processor"
      (str/join ","
                ["org.apache.logging.log4j.core.config.plugins.processor.GraalVmProcessor"
                 "org.apache.logging.log4j.core.config.plugins.processor.PluginProcessor"])
      (format "-Alog4j.graalvm.groupId=%s" library-group-id)
      (format "-Alog4j.graalvm.artifactId=%s" artifact-id)])

    opts))

(defn- project-artifact-opts
  ([root-path] (project-artifact-opts root-path {}))
  ([root-path opts]
   (-> {:artifact-id     (artifact-id root-path)
        :aliases         (into (if (library-project? root-path)
                                 (if snapshot?
                                   #{:snapshot}
                                   #{:release})
                                 #{:otel})
                               (:aliases opts))
        :group-id        (group-id root-path)
        :root-path       root-path
        :main            (or (:main opts)
                             (when-not (library-project? root-path)
                               (symbol (str "example." (artifact-id root-path)))))
        :tag             (when (library-project? root-path)
                               @head-sha-1)
        :uber-classifier (or (:uber-classifier opts) "all")}
       artifact-opts
       modify-artifact-opts)))

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

(defn- add-provided-deps
  "Adds `deps` as dependencies with `provided` scope to `pom` zipper."
  [pom deps]
  (let [deps-loc (->> pom
                      (iterate zip/next)
                      (filter #(= ::pom/dependencies (:tag (zip/node %))))
                      first)
        provided-deps-elems (map (fn [[k v]]
                                   (xml/sexp-as-element
                                    [::pom/dependency
                                     [::pom/groupId (namespace k)]
                                     [::pom/artifactId (name k)]
                                     [::pom/version (:mvn/version v)]
                                     [::pom/scope "provided"]]))
                                 deps)]
    (reduce zip/append-child deps-loc provided-deps-elems)))

(defn- write-pom*
  "Writes `pom.xml` and `pom.properties` files. Same as
  `clojure.tools.build.api/write-pom`, but also includes `extra-deps` in
  `provided` alias as dependencies with `provided` scope."
  [{:keys [group-id artifact-id]
    :as   opts}]
  (println "Writing POM files for" (group-artifact-id group-id artifact-id) "...")
  (b/write-pom opts)
  (when-let [provided-deps (-> opts
                               :basis
                               :aliases
                               :provided
                               :extra-deps)]
    (let [pom-str (with-open [reader (io/reader (b/pom-path opts))]
                    (-> (xml/parse reader {:skip-whitespace true})
                        zip/xml-zip
                        (add-provided-deps provided-deps)
                        zip/root
                        xml/indent-str))]
      (spit (b/pom-path opts) pom-str))))

(defn- clean-artifact
  [{:keys [target-dir]}]
  (println "Cleaning build dir" target-dir "...")
  (b/delete {:path target-dir}))

(defn- src-artifact
  [{:keys [class-dir resource-dirs src-dirs]}]
  (println "Copying src and resource dirs ...")
  (b/copy-dir {:src-dirs   (into src-dirs resource-dirs)
               :target-dir class-dir}))

(defn- javac-artifact
  [{:keys [java-src-dirs root-path basis-provided]
    :as   opts}]
  (when (seq (apply globs root-path (map #(format "%s/**.java" %) java-src-dirs)))
    (println "Compiling Java source files ...")
    (b/javac (assoc opts :src-dirs java-src-dirs :basis basis-provided))))

(defn- jar-artifact
  [{:keys [jar-file]
    :as   opts}]
  (clean-artifact opts)
  (javac-artifact opts)
  (src-artifact opts)
  (write-pom* opts)
  (println "Building jar" jar-file "...")
  (b/jar opts))

(defn- uberjar-artifact
  [{:keys [uber-file]
    :as   opts}]
  (clean-artifact opts)
  (javac-artifact opts)
  (src-artifact opts)
  (println "Compiling Clojure source files ...")
  (b/compile-clj opts)
  (println "Building AOT compiled uberjar" uber-file "...")
  (b/uber opts))

(defn- native-artifact
  [{:keys [native-file uber-file]
    :as   opts}]
  (uberjar-artifact opts)
  (println "Compiling native image ...")
  (checked-process {:command-args ["native-image"   ;
                                   "-jar" uber-file ;
                                   "--features=clj_easy.graal_build_time.InitClojureClasses" ;
                                   "--no-fallback" ;
                                   "-o" native-file]}))

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

(defn javac
  "Compile Java source files for all clj-otel-* libraries."
  [_]
  (doseq [root_path library-project-paths]
    (b/with-project-root root_path
      (let [opts (project-artifact-opts root_path)]
        (clean-artifact opts)
        (javac-artifact opts)))))

(defn uber
  "Build uberjar for demo project that has -main fn.

  Invoke with e.g.
  clojure -T:build uber :path '\"examples/divisor-app\"'"
  [{:keys [path aliases]}]
  (b/with-project-root path
    (uberjar-artifact (project-artifact-opts path {:aliases aliases}))))

(defn native
  "Build native executable for demo project that has -main fn. Assumes a
  working installation of GraalVM native-image. Also assumes project alias
  :native that has extra-dep com.github.clj-easy/graal-build-time.

  Invoke with e.g.
  clojure -T:build native :path '\"examples/divisor-app\"'"
  [{:keys [path aliases]
    :or   {aliases #{}}}]
  (b/with-project-root path
    (native-artifact (project-artifact-opts path
                                            {:aliases         (conj aliases :native)
                                             :uber-classifier "native"}))))

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

(defn lint
  "Lint all clj-otel-* libraries, example applications and tutorial source
  files using clj-kondo. Assumes a working installation of `clj-kondo`
  executable binary."
  [_]
  (let [paths (->> project-paths
                   (mapcat #(list (str % "/src") (str % "/dev")))
                   (filter #(.isDirectory (io/file %))))]
    (checked-process {:command-args (concat ["clj-kondo" "--lint" "build.clj"] paths)})))

(defn outdated
  "Check all clj-otel-* libraries, example applications and tutorials for
  outdated dependencies using antq. Dependencies on clj-otel-* projects are
  not checked."
  [_]
  (run-task {:main-opts
             ["--directory" (str/join ":" project-paths)
              "--skip" "pom"
              "--exclude" (str/join ":" (conj group-artifact-ids "org.clojure/clojure"))
              "--no-changes"]}
            #{:antq}))

(defn fmt
  "Apply formatting to all *.clj and *.edn source files using zprint. Assumes a
  working installation of `zprint` executable binary."
  [_]
  (let [project-files (mapcat #(globs % "src/**.clj" "dev/**.clj" "*.edn" "resources/**.edn")
                       project-paths)
        other-files   (globs "." "*.clj" "*.edn" ".clj-kondo/config.edn" "doc/**.edn")
        files         (concat project-files other-files)
        config-url    (-> ".zprint.edn"
                          io/file
                          io/as-url
                          str)]
    (checked-process {:command-args (concat ["zprint" "--url-only" config-url "-fsw"] files)})))
