(ns example.solar-system-load-gen.main
  "Load generator application that sends rate-controlled random requests to a
   solar-system-service instance."
  (:require [aero.core :as aero]
            [clojure.java.io :as io]
            [example.common.load-gen :as lg])
  (:gen-class))

(def ^:private config
  {})

(defn- solar-system-endpoint
  []
  (get-in config [:endpoints :solar-system-service]))

(defn solar-system-req
  "Returns a request for statistics on the given planet."
  [planet]
  {:method       :get
   :url          (str (solar-system-endpoint) "/statistics")
   :query-params {:planet planet}
   :accept       "application/json"})

(defn rand-valid-req
  "Returns a random valid request."
  []
  (solar-system-req (rand-nth ["mercury" "venus" "earth" "mars" "jupiter" "uranus" "neptune"])))

(defn rand-invalid-req-pluto
  "Returns an invalid request for statistics on Pluto."
  []
  (solar-system-req "pluto"))

(defn rand-invalid-req-saturn
  "Returns an invalid request designed to cause a simulated intermittent
   exception in planet-service."
  []
  (solar-system-req "saturn"))

(defn rand-invalid-req-not-found
  "Returns a random invalid request for an unknown route."
  []
  {:method :get
   :url    (str (solar-system-endpoint) (rand-nth ["/unknown" "/not-here" "/invalid"]))})

(defn requests
  "For a given start time, return a signal [t req] of random requests."
  [start-t]
  (let [signals [(lg/periodic start-t 2000 (repeatedly rand-valid-req))
                 (lg/periodic start-t 9000 (repeatedly rand-invalid-req-pluto))
                 (lg/periodic start-t 11000 (repeatedly rand-invalid-req-saturn))
                 (lg/periodic start-t 13000 (repeatedly rand-invalid-req-not-found))]
        signal  (lg/multiplex signals)]
    (sequence (lg/jitter 7000) signal)))

(defn gen-load
  "Generates load for solar-system-service."
  []
  (alter-var-root #'config (constantly (aero/read-config (io/resource "config.edn"))))
  (lg/do-requests (requests (System/currentTimeMillis))))

(defn -main
  "Application entry point for solar-system-service load generator."
  [& _args]
  (gen-load))
