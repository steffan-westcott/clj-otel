(ns steffan-westcott.otel.propagator.b3
  "Access to B3 propagation protocol implementation."
  (:import (io.opentelemetry.extension.trace.propagation B3Propagator)))

(defn b3-propagator
  "Returns an implementation of the B3 propagation protocol."
  [{:keys [inject-format]
    :or   {inject-format :single-header}}]
  (case inject-format
    :single-header (B3Propagator/injectingSingleHeader)
    :multi-headers (B3Propagator/injectingMultiHeaders)))