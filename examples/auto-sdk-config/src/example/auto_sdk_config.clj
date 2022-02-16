(ns example.auto-sdk-config
  (:require [steffan-westcott.clj-otel.api.trace.span :as span]))


(defn square
  "Returns the square of a number."
  [n]
  (span/with-span! {:name       "squaring"
                    :attributes {:my-arg n}}
    (Thread/sleep 500)
    (span/add-span-data! {:event {:name "my event"}})
    (* n n)))

;;;;;;;;;;;;;

(square 9)

