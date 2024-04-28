(ns example.solar-system-load-gen.load
  "Functions for generating a load of timed random HTTP requests."
  (:require [example.common.load-gen.requests :as requests]
            [example.common.load-gen.signal :as sig]
            [example.solar-system-load-gen.env :refer [config]]))

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

(defn signal
  "For a given start time, return a signal [t req] of random requests."
  [start-t]
  (let [signals [(sig/periodic start-t 2000 (repeatedly rand-valid-req))
                 (sig/periodic start-t 9000 (repeatedly rand-invalid-req-pluto))
                 (sig/periodic start-t 11000 (repeatedly rand-invalid-req-saturn))
                 (sig/periodic start-t 13000 (repeatedly rand-invalid-req-not-found))]
        signal  (sig/multiplex signals)]
    (sequence (sig/jitter 7000) signal)))

(defn start-load
  "Generates a load of timed random HTTP requests."
  [conn-mgr client]
  (requests/do-requests conn-mgr client (signal (System/currentTimeMillis))))
