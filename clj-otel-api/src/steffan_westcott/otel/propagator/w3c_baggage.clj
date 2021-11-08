(ns steffan-westcott.otel.propagator.w3c-baggage
  "Access to W3C baggage header propagation implementation."
  (:import (io.opentelemetry.api.baggage.propagation W3CBaggagePropagator)))

(defn w3c-baggage-propagator
  "Returns an implementation of the W3C specification for baggage header
  propagation."
  []
  (W3CBaggagePropagator/getInstance))