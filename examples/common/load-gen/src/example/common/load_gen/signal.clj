(ns example.common.load-gen.signal
  "Functions for building a sequence of timed HTTP requests.")

(defn jitter
  "Transducer that adds random integer values [0, max-delta) to t of input
   vectors [t x], where input and output vectors are (re)ordered such that t is
   monotonically increasing."
  [max-delta]
  (comp
   (fn [rf]
     (let [prev (volatile! [])]
       (fn
         ([] (rf))
         ([result]
          (rf (unreduced (rf result (sort-by first @prev)))))
         ([result [t x]]
          (let [{before-t   true
                 t-or-later false}
                (group-by #(< (first %) t) @prev)]
            (vreset! prev (conj t-or-later [(+ t (rand-int max-delta)) x]))
            (rf result (sort-by first before-t)))))))
   cat))

(defn- multiplex*
  [sigx sigy]
  (lazy-seq (if-let [x (first sigx)]
              (if-let [y (first sigy)]
                (if (< (first x) (first y))
                  (cons x (multiplex* (rest sigx) sigy))
                  (cons y (multiplex* sigx (rest sigy))))
                sigx)
              sigy)))

(defn multiplex
  "Merges a collection of signals, where a signal is a lazy seq of vectors [t x]
   with t monotonically increasing."
  [coll-sig]
  (reduce multiplex* () coll-sig))

(defn periodic
  "Given start-t start time, period in ms and a seq of reqs, return a periodic
   signal [t req]."
  [start-t period reqs]
  (map vector (iterate #(+ % period) start-t) reqs))
