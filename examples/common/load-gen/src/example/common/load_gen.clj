(ns example.common.load-gen
  "Utilities for building and issuing randomised, rate-controlled requests for
   example microservices."
  (:require [clj-http.client :as client]
            [clj-http.conn-mgr :as conn]
            [clj-http.core :as http-core]
            [clojure.tools.logging :as log]))

(def ^:private async-conn-mgr
  (delay (conn/make-reusable-async-conn-manager {})))

(def ^:private async-client
  (delay (http-core/build-async-http-client {} @async-conn-mgr)))

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

(defn- sleep-until
  "Sleep the current thread until system time t."
  [t]
  (let [d (- t (System/currentTimeMillis))]
    (when (pos? d)
      (Thread/sleep d))))

(defn do-requests
  "Given a (possibly infinite) seq of vectors, for each vector [t req] perform
   an async HTTP req at system time t. It is assumed t monotonically increases
   in the sequence."
  [signal]
  (doseq [[t req] signal]
    (sleep-until t)
    (log/debugf "Sending request : %s" req)
    (try
      (client/request (conj req
                            {:async true
                             :throw-exceptions false
                             :connection-manager @async-conn-mgr
                             :http-client @async-client
                             :multi-param-style :comma-separated})
                      (fn [response]
                        (log/debugf "Received response : %s"
                                    {:request  req
                                     :response (select-keys response [:status :body])}))
                      (fn [error]
                        (log/debugf error "Error processing request : %s" req)))
      (catch Exception e
        (log/debugf e "Error sending request : %s" req)))))
