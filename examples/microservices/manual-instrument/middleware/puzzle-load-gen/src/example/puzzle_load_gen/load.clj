(ns example.puzzle-load-gen.load
  "Functions for generating a load of timed random HTTP requests."
  (:require [example.common.load-gen.requests :as requests]
            [example.common.load-gen.signal :as sig]
            [example.puzzle-load-gen.env :refer [config]]))

(defn- puzzle-endpoint
  []
  (get-in config [:endpoints :puzzle-service]))

(defn puzzle-req
  "Returns a request for words of given types."
  [types]
  {:method       :get
   :url          (str (puzzle-endpoint) "/puzzle")
   :query-params {:types types}
   :accept       "application/json"})

(defn- rand-valid-types
  []
  (repeatedly (inc (rand-int 4)) #(rand-nth ["noun" "verb" "adjective"])))

(defn rand-invalid-req
  "Returns a puzzle request where some valid types are replaced by types in
   `invalid-types`."
  [invalid-types]
  (let [xs    (rand-valid-types)
        n     (inc (rand-int (count xs)))
        ys    (repeatedly n #(rand-nth invalid-types))
        types (concat (drop n xs) ys)]
    (puzzle-req (shuffle types))))

(defn- rand-valid-req
  "Returns a random valid request."
  []
  (puzzle-req (rand-valid-types)))

(defn- rand-invalid-req-unknown-type
  "Returns a random invalid request that includes unknown types."
  []
  (rand-invalid-req ["bogus" "weird" "strange"]))

(defn- rand-invalid-req-fault
  "Returns a random invalid request designed to cause a simulated intermittent
   exception in random-word-service."
  []
  (rand-invalid-req ["fault"]))

(defn- rand-invalid-req-not-found
  "Returns a random invalid request for an unknown route."
  []
  {:method :get
   :url    (str (puzzle-endpoint) (rand-nth ["/unknown" "/not-here" "/invalid"]))})

(defn- signal
  "For a given start time, return a signal [t req] of random requests."
  [start-t]
  (let [signals [(sig/periodic start-t 2000 (repeatedly #(rand-valid-req)))
                 (sig/periodic start-t 9000 (repeatedly #(rand-invalid-req-unknown-type)))
                 (sig/periodic start-t 11000 (repeatedly #(rand-invalid-req-fault)))
                 (sig/periodic start-t 13000 (repeatedly #(rand-invalid-req-not-found)))]
        signal  (sig/multiplex signals)]
    (sequence (sig/jitter 7000) signal)))

(defn start-load
  "Generates a load of timed random HTTP requests."
  [conn-mgr client]
  (requests/do-requests conn-mgr client (signal (System/currentTimeMillis))))
