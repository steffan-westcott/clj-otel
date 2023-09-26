(ns example.divisor-app
  (:require [steffan-westcott.clj-otel.api.metrics.instrument :as instrument]
            [steffan-westcott.clj-otel.api.trace.span :as span]
            [steffan-westcott.clj-otel.instrumentation.runtime-telemetry-java8 :as
             runtime-telemetry]
            [steffan-westcott.clj-otel.sdk.autoconfigure :as autoconfig]))

(defonce
  ^{:doc "Delay containing counter that records the number of greatest common divisors calculated."}
  gcd-count
  (delay (instrument/instrument {:name "app.divisor.gcd-count"
                                 :instrument-type :counter
                                 :unit "{greatest common divisors}"
                                 :description
                                 "The number of greatest common divisors calculated"})))

(defn- gcd*
  [x y]
  (if (zero? x)
    y
    (recur (mod y x) x)))

(defn gcd
  "Returns the greatest common divisor of x and y."
  [x y]
  (span/with-span! {:name       "Calculating gcd"
                    :attributes {:app.divisor/args [x y]}}
    (span/add-span-data! {:event {:name "my event"}})
    (instrument/add! @gcd-count {:value 1})
    (gcd* x y)))

;;;;;;;;;;;;;

(comment

  ;; Initialise OpenTelemetry SDK instance and set as default used by `clj-otel`
  (defonce _otel-sdk
    (autoconfig/init-otel-sdk!))

  ;; Optional - Add JVM metrics in export
  (defonce jvm-reg
    (runtime-telemetry/register!))

  ;; Exercise the application
  (gcd 18 24)

  ;; Remove JVM metrics in export
  (runtime-telemetry/close! jvm-reg)

  ;
)