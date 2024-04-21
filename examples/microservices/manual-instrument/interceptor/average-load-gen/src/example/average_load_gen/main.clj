(ns example.average-load-gen.main
  "Load generator application that sends rate-controlled random requests to an
   average-service instance."
  (:require [aero.core :as aero]
            [clojure.java.io :as io]
            [example.common.load-gen :as lg])
  (:gen-class))

(def ^:private config
  {})

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

(defn requests
  "For a given start time, return a signal [t req] of random requests."
  [start-t]
  (let [signals [(lg/periodic start-t 2000 (repeatedly rand-valid-req))
                 (lg/periodic start-t 9000 (repeatedly rand-invalid-req-first-arg-zero))
                 (lg/periodic start-t 11000 (repeatedly rand-invalid-req-unlucky-13))
                 (lg/periodic start-t 13000 (repeatedly rand-invalid-req-not-found))]
        signal  (lg/multiplex signals)]
    (sequence (lg/jitter 7000) signal)))

(defn gen-load
  "Generates load for average-service."
  []
  (alter-var-root #'config (constantly (aero/read-config (io/resource "config.edn"))))
  (lg/do-requests (requests (System/currentTimeMillis))))

(defn -main
  "Application entry point for average-service load generator."
  [& _args]
  (gen-load))
