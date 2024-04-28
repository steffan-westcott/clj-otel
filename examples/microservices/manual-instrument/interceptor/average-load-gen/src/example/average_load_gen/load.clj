(ns example.average-load-gen.load
  "Functions for generating a load of timed random HTTP requests."
  (:require [example.average-load-gen.env :refer [config]]
            [example.common.load-gen.requests :as requests]
            [example.common.load-gen.signal :as sig]))

(defn- average-endpoint
  []
  (get-in config [:endpoints :average-service]))

(defn average-req
  "Returns a request for averages taken from the given numbers."
  [nums]
  {:method       :get
   :url          (str (average-endpoint) "/average")
   :query-params {:nums nums}
   :accept       "application/json"})

(defn- rand-odds
  []
  (repeatedly (rand-int 5) #(inc (* 2 (rand-int 10)))))

(defn- rand-evens
  []
  (repeatedly (rand-int 5) #(* 2 (inc (rand-int 10)))))

(defn- rand-valid-odds
  "Returns a random collection of odd numbers which do not sum to 13."
  []
  (first (remove #(= 13 (reduce + %)) (repeatedly rand-odds))))

(defn- rand-invalid-odds
  "Returns a random collection of odd numbers which sum to 13."
  []
  (rand-nth [[1 3 9] [1 5 7] [3 5 5] [13]]))

(defn- rand-valid-nums
  []
  (shuffle (into (rand-valid-odds) (rand-evens))))

(defn rand-valid-req
  "Returns a random valid request."
  []
  (average-req (rand-valid-nums)))

(defn rand-invalid-req-first-arg-zero
  "Returns a random invalid request that has first argument of 0."
  []
  (average-req (into [0] (drop 1 (rand-valid-nums)))))

(defn rand-invalid-req-unlucky-13
  "Returns a random invalid request designed to cause a simulated intermittent
   exception in sum-service."
  []
  (average-req (shuffle (into (rand-invalid-odds) (rand-evens)))))

(defn rand-invalid-req-not-found
  "Returns a random invalid request for an unknown route."
  []
  {:method :get
   :url    (str (average-endpoint) (rand-nth ["/unknown" "/not-here" "/invalid"]))})

(defn signal
  "For a given start time, return a signal [t req] of random requests."
  [start-t]
  (let [signals [(sig/periodic start-t 2000 (repeatedly rand-valid-req))
                 (sig/periodic start-t 9000 (repeatedly rand-invalid-req-first-arg-zero))
                 (sig/periodic start-t 11000 (repeatedly rand-invalid-req-unlucky-13))
                 (sig/periodic start-t 13000 (repeatedly rand-invalid-req-not-found))]
        signal  (sig/multiplex signals)]
    (sequence (sig/jitter 7000) signal)))

(defn start-load
  "Generates a load of timed random HTTP requests."
  [conn-mgr client]
  (requests/do-requests conn-mgr client (signal (System/currentTimeMillis))))
