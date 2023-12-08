(ns steffan-westcott.clj-otel.propagator.w3c-baggage
  "Access to [W3C baggage header propagation](https://www.w3.org/TR/baggage/)
   implementation."
  (:import (io.opentelemetry.api.baggage.propagation W3CBaggagePropagator)))

(defn w3c-baggage-propagator
  "Returns an implementation of the [W3C specification for baggage header
   propagation](https://www.w3.org/TR/baggage/)."
  ^W3CBaggagePropagator []
  (W3CBaggagePropagator/getInstance))