(ns steffan-westcott.clj-otel.propagator.b3
  "Access to [B3 propagation protocol](https://github.com/openzipkin/b3-propagation)
  implementation."
  (:import (io.opentelemetry.extension.trace.propagation B3Propagator)))

(defn b3-propagator
  "Returns an implementation of the [B3 propagation protocol](https://github.com/openzipkin/b3-propagation).
  Takes an option map as follows:

  | key            | description |
  |----------------|-------------|
  |`:inject-format`| Header injection format, one of `:single-header` or `:multi-headers` (default: `:single-header`)."
  ([]
   (b3-propagator {}))
  ([{:keys [inject-format]
     :or   {inject-format :single-header}}]
   (case inject-format
     :single-header (B3Propagator/injectingSingleHeader)
     :multi-headers (B3Propagator/injectingMultiHeaders))))