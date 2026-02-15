(ns ^:deprecated steffan-westcott.clj-otel.propagator.open-tracing
  "DEPRECATED: OpenTracing propagation protocol is deprecated in the
   OpenTelemetry specification.
   Access to implementation of protocol used by OpenTracing Basic Tracers."
  (:import (io.opentelemetry.extension.trace.propagation OtTracePropagator)))

(defn ^:deprecated trace-propagator
  "DEPRECATED: OpenTracing propagation protocol is deprecated in the
   OpenTelemetry specification.
   Returns an implementation of the protocol used by OpenTracing Basic Tracers."
  ^OtTracePropagator []
  (OtTracePropagator/getInstance))