(ns steffan-westcott.clj-otel.propagator.w3c-trace-context
  "Access to [W3C Trace Context propagation
   protocol](https://www.w3.org/TR/trace-context/) implementation."
  (:import (io.opentelemetry.api.trace.propagation W3CTraceContextPropagator)))

(defn w3c-trace-context-propagator
  "Returns an implementation of the [W3C Trace Context propagation
   protocol](https://www.w3.org/TR/trace-context/)."
  []
  (W3CTraceContextPropagator/getInstance))