(ns example.auto-sdk-config
  (:require [steffan-westcott.clj-otel.api.trace.span :as span]
            [steffan-westcott.clj-otel.instrumentation.runtime-metrics :as runtime-metrics]))


(defn square
  "Returns the square of a number."
  [n]
  (span/with-span! {:name       "squaring"
                    :attributes {:app.square/n n}}
    (Thread/sleep 500)
    (span/add-span-data! {:event {:name "my event"}})
    (* n n)))

;;;;;;;;;;;;;

(defonce ^{:doc "JVM metrics registration"} _jvm-reg (runtime-metrics/register!))

(square 9)

