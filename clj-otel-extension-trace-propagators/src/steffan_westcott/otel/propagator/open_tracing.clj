(ns steffan-westcott.otel.propagator.open-tracing
  "Access to OpenTracing Basic Tracers propagation protocol implementation."
  (:import (io.opentelemetry.extension.trace.propagation OtTracePropagator)))

(defn trace-propagator
  "Returns a [[TextMapPropagator]] that implements the protocol used by
  OpenTracing Basic Tracers."
  []
  (OtTracePropagator/getInstance))