(ns steffan-westcott.otel.propagator.w3c-trace-context
  "Access to W3C TraceContext propagation protocol implementation."
  (:import (io.opentelemetry.api.trace.propagation W3CTraceContextPropagator)))

(defn w3c-trace-context-propagator
  "Returns an implementation of the W3C TraceContext propagation protocol. This
  is the default propagator used for context propagation."
  []
  (W3CTraceContextPropagator/getInstance))