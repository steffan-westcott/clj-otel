(ns steffan-westcott.clj-otel.propagator.jaeger
  "Access to Jaeger propagation protocol implementation."
  (:import (io.opentelemetry.extension.trace.propagation JaegerPropagator)))

(defn jaeger-propagator
  "Returns an implementation of the Jaeger propagation protocol."
  ^JaegerPropagator []
  (JaegerPropagator/getInstance))