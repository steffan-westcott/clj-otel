(ns example.sentence-summary-load-gen.load
  "Functions for generating a load of timed random HTTP requests."
  (:require [clojure.string :as str]
            [example.common.load-gen.requests :as requests]
            [example.common.load-gen.signal :as sig]
            [example.sentence-summary-load-gen.env :refer [config]]))


(defn- sentence-summary-endpoint
  []
  (get-in config [:endpoints :sentence-summary-service]))

(defn sentence-summary-req
  "Returns a request for a summary of a sentence containing the given words."
  [words]
  {:method       :get
   :url          (str (sentence-summary-endpoint) "/summary")
   :query-params {:sentence (str/join " " words)}
   :accept       "application/json"})

(defn- rand-words
  []
  (repeatedly (reduce + 1 (repeatedly 8 #(rand-int 2)))
              #(rand-nth ["a" "ability" "amazing" "anchor" "apple" "be" "chair" "dancing"
                          "dedicated" "exchange" "excited" "fascinate" "festival" "film" "flame"
                          "green" "grip" "herald" "level" "light" "musical" "one" "praise" "revive"
                          "seek" "shade" "sharpen" "skill" "split" "tango" "they" "tree" "trip"
                          "voyage" "walk" "walk" "will" "you"])))

(defn- rand-invalid-words
  "Returns a puzzle request where some valid types are replaced by types in
   `invalid-types`."
  [invalid-word]
  (shuffle (conj (drop 1 (rand-words)) invalid-word)))

(defn rand-valid-req
  "Returns a random valid request."
  []
  (sentence-summary-req (rand-words)))

(defn rand-invalid-req-bad-word-arg
  "Returns a random invalid request that includes a bad word argument."
  []
  (sentence-summary-req (rand-invalid-words "problem")))

(defn rand-invalid-req-boom
  "Returns a random invalid request designed to cause a simulated intermittent
   exception in word-length-service."
  []
  (sentence-summary-req (rand-invalid-words "boom")))

(defn rand-invalid-req-not-found
  "Returns a random invalid request for an unknown route."
  []
  {:method :get
   :url    (str (sentence-summary-endpoint) (rand-nth ["/unknown" "/not-here" "/invalid"]))})

(defn signal
  "For a given start time, return a signal [t req] of random requests."
  [start-t]
  (let [signals [(sig/periodic start-t 2000 (repeatedly rand-valid-req))
                 (sig/periodic start-t 9000 (repeatedly rand-invalid-req-bad-word-arg))
                 (sig/periodic start-t 11000 (repeatedly rand-invalid-req-boom))
                 (sig/periodic start-t 13000 (repeatedly rand-invalid-req-not-found))]
        signal  (sig/multiplex signals)]
    (sequence (sig/jitter 7000) signal)))

(defn start-load
  "Generates a load of timed random HTTP requests."
  [conn-mgr client]
  (requests/do-requests conn-mgr client (signal (System/currentTimeMillis))))