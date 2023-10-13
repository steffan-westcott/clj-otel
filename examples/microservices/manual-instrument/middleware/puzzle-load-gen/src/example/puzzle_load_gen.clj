(ns example.puzzle-load-gen
  "Load generator application that sends rate-controlled random requests to a
   puzzle-service instance."
  (:require [aero.core :as aero]
            [clojure.java.io :as io]
            [example.common.load-gen :as lg])
  (:gen-class))

(def ^:private config
  {})

(defn- puzzle-endpoint
  []
  (get-in config [:endpoints :puzzle-service]))

(defn puzzle-req
  "Returns a request for words of given types."
  [types]
  {:method       :get
   :url          (str (puzzle-endpoint) "/puzzle")
   :query-params {:types types}})

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

(defn rand-valid-req
  "Returns a random valid request."
  []
  (puzzle-req (rand-valid-types)))

(defn rand-invalid-req-unknown-type
  "Returns a random invalid request that includes unknown types."
  []
  (rand-invalid-req ["bogus" "weird" "strange"]))

(defn rand-invalid-req-fault
  "Returns a random invalid request designed to cause a simulated intermittent
   exception in random-word-service."
  []
  (rand-invalid-req ["fault"]))

(defn rand-invalid-req-not-found
  "Returns a random invalid request for an unknown route."
  []
  {:method :get
   :url    (str (puzzle-endpoint) (rand-nth ["/unknown" "/not-here" "/invalid"]))})

(defn requests
  "For a given start time, return a signal [t req] of random requests."
  [start-t]
  (let [signals [(lg/periodic start-t 2000 (repeatedly rand-valid-req))
                 (lg/periodic start-t 9000 (repeatedly rand-invalid-req-unknown-type))
                 (lg/periodic start-t 11000 (repeatedly rand-invalid-req-fault))
                 (lg/periodic start-t 13000 (repeatedly rand-invalid-req-not-found))]
        signal  (lg/multiplex signals)]
    (sequence (lg/jitter 7000) signal)))

(defn gen-load
  "Generates load for puzzle-service."
  []
  (alter-var-root #'config (constantly (aero/read-config (io/resource "config.edn"))))
  (lg/do-requests (requests (System/currentTimeMillis))))

(defn -main
  "Application entry point for puzzle-service load generator."
  [& _args]
  (gen-load))

(comment
  (gen-load)
  ;
)