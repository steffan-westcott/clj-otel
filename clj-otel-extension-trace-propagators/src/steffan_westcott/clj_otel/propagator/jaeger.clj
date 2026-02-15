(ns ^:deprecated steffan-westcott.clj-otel.propagator.jaeger
  "DEPRECATED: Jaeger propagation protocol is deprecated in the OpenTelemetry
   specification.
   Access to Jaeger propagation protocol implementation."
  (:import (io.opentelemetry.extension.trace.propagation JaegerPropagator)))

(defn ^:deprecated jaeger-propagator
  "Returns an implementation of the Jaeger propagation protocol."
  ^JaegerPropagator []
  (JaegerPropagator/getInstance))