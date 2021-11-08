(ns steffan-westcott.otel.propagator.jaeger
  "Access to Jaeger propagation protocol implementation."
  (:import (io.opentelemetry.extension.trace.propagation JaegerPropagator)))

(defn jaeger-propagator
  "Returns a [[TextMapPropagator]] that implements the Jaeger propagation
  protocol."
  []
  (JaegerPropagator/getInstance))