(ns steffan-westcott.clj-otel.propagator.open-tracing
  "Access to implementation of protocol used by OpenTracing Basic Tracers."
  (:import (io.opentelemetry.extension.trace.propagation OtTracePropagator)))

(defn trace-propagator
  "Returns an implementation of the protocol used by OpenTracing Basic
  Tracers."
  []
  (OtTracePropagator/getInstance))