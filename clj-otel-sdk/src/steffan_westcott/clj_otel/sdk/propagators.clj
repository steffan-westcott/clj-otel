(ns ^:no-doc steffan-westcott.clj-otel.sdk.propagators
  "Programmatic configuration of `ContextPropagators`, a component of the
   OpenTelemetry SDK. This namespace is for internal use only."
  (:require [steffan-westcott.clj-otel.propagator.w3c-baggage :as w3c-baggage]
            [steffan-westcott.clj-otel.propagator.w3c-trace-context :as w3c-trace])
  (:import (io.opentelemetry.context.propagation ContextPropagators TextMapPropagator)))

(defn default
  "Returns collection of default context propagators."
  []
  [(w3c-trace/w3c-trace-context-propagator) (w3c-baggage/w3c-baggage-propagator)])

(defn context-propagators
  "Returns a `ContextPropagators` instance containing the given ordered
   collection of `TextMapPropagator`s."
  ^ContextPropagators [text-map-propagators]
  (let [^Iterable props (vec text-map-propagators)]
    (ContextPropagators/create (TextMapPropagator/composite props))))
