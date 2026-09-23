(ns steffan-westcott.clj-otel.instrumentation.api.config.glob
  "Support for string glob patterns used in instrumentation configuration."
  (:import io.opentelemetry.instrumentation.api.config.IncludeExclude
           java.util.Collection))

(defn maybe-glob
  "Returns a predicate which matches provided glob patterns, or nil if no
   patterns are provided. A glob pattern is a string where `?` is a single
   wildcard character and `*` is a (possibly zero-length) string of wildcard
   characters. Takes a map as follows:

   | key       | description |
   |-----------|-------------|
   |`:excluded`| Collection of glob patterns, where predicate returns `false` if any match input string. Has precedence over `:included`.
   |`:included`| Collection of glob patterns, where predicate returns `true` if collection is empty or any pattern matches input string, `false` otherwise."
  [{:keys [excluded included]}]
  (when (or (seq excluded) (seq included))
    (let [^IncludeExclude matcher (.build (cond-> (IncludeExclude/builder)
                                            excluded (.setExcluded ^Collection (vec excluded))
                                            included (.setIncluded ^Collection (vec included))))]
      (fn matches? [s]
        (.matches matcher s)))))
