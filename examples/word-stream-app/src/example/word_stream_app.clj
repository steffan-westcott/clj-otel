(ns example.word-stream-app
  "A manually instrumented example core.async.flow application."
  (:require [clojure.core.async :as async]
            [clojure.core.async.flow :as flow]
            [steffan-westcott.clj-otel.api.metrics.instrument :as instrument]
            [steffan-westcott.clj-otel.api.trace.span :as span]))



(defonce ^{:doc "Delay containing counter of word view updates."} view-update-count
  (delay (instrument/instrument {:name        "app.word-stream.view-update-count"
                                 :instrument-type :counter
                                 :unit        "{updates}"
                                 :description "The number of word view updates"})))



(defonce ^{:doc "Atom containing vector of the tail of input elements, most recent last."} ;
         tail
  (atom []))



(defonce
  ^{:doc "Atom containing vector of most recently used (MRU) elements in input, most recent first."} ;
  mru
  (atom []))



(defn- last-n
  "Returns last `n` elements of vector `vec`."
  [vec n]
  (subvec vec (max 0 (- (count vec) n))))



(defn- first-n
  "Returns first `n` elements of vector `vec`."
  [vec n]
  (subvec vec 0 (min n (count vec))))



(defn update-tail
  "core.async.flow step function that updates atom `tail`."
  ([]
   {:params   {:len "Maximum length of tail"}
    :ins      {:input "Map with `context` and vector `xs`"}
    :workload :compute})
  ([state] state)
  ([state _] state)
  ([state _in msg]

   (span/with-span-binding [context {:name   (::flow/pid state)
                                     :parent (:context msg)}]
     (let [t (swap! tail #(last-n (into % (:xs msg)) (:len state)))]
       (instrument/add! @view-update-count
                        {:value      1
                         :attributes {:view :tail}})
       (span/add-span-data! {:context context
                             :event   {:name       "Updated tail"
                                       :attributes {:system/most-recent (peek t)}}})
       [state nil]))))



(defn update-mru
  "core.async.flow step function that updates atom `mru`."
  ([]
   {:params   {:len "Maximum length of computed MRU"}
    :ins      {:input "Map with `context` and vector `xs`"}
    :workload :compute})
  ([state] state)
  ([state _] state)
  ([state _in msg]

   (span/with-span-binding [context {:name   (::flow/pid state)
                                     :parent (:context msg)}]
     (let [m (swap! mru #(reduce (fn [mru word]
                                   (first-n (into [word] (remove #{word}) mru)
                                            (:len state)))
                                 %
                                 (:xs msg)))]
       (instrument/add! @view-update-count
                        {:value      1
                         :attributes {:view :mru}})
       (span/add-span-data! {:context context
                             :event   {:name       "Updated MRU"
                                       :attributes {:system/mru-top3 (first-n m 3)}}})
       [state nil]))))



(defn batch
  "core.async.flow step function that takes a vector of elements as input and
   forms batched elements as output. An empty input causes buffered elements to
   be flushed to output."
  ([]
   {:params   {:size "Maximum size of batch to output"}
    :ins      {:input "Map with `context` and vector `xs`; if `xs` is empty, flush buffer"}
    :outs     {:output
               "Map with `context` and `xs`, where `xs` is vector with at most `size` elements"}
    :workload :compute})
  ([state] (assoc state :buf []))
  ([state _] state)
  ([{:keys [buf size]
     :as   state} _in
    {:keys [xs]
     :as   msg}]

   (span/with-span-binding [context {:name   (::flow/pid state)
                                     :parent (:context msg)}]
     (if (seq xs)

       ;; Add xs to buf. Output batches of `size` elements each
       (let [buf     (into buf xs)
             i       (- (count buf) (mod (count buf) size))
             batches (partitionv size (subvec buf 0 i))
             buf     (subvec buf i)]
         (span/add-span-data! {:context context
                               :event   {:name       "Emitting batched data"
                                         :attributes {:system/batch-count (count batches)
                                                      :system/batch-size  size}}})
         [(assoc state :buf buf)
          {:output (map (fn [batch]
                          {:context context
                           :xs      batch})
                        batches)}])

       ;; Flush all contents of buf
       (do
         (span/add-span-data! {:context context
                               :event   {:name       "Flushing buffered data"
                                         :attributes {:system/batch-size (count buf)}}})
         [(assoc state :buf [])
          {:output (when (seq buf)
                     [{:context context
                       :xs      buf}])}])))))



(defn ingest
  "core.async.flow step function that takes a collection of ordered elements as
   input and outputs a vector of those elements."
  ([]
   {:ins      {:input "Collection of elements to ingest"}
    :outs     {:output "Map with `context` and vector `xs`; if `xs` is empty, flush buffer"}
    :workload :compute})
  ([state] state)
  ([state _] state)
  ([state _in xs]

   (span/with-span-binding [context {:name       (::flow/pid state)
                                     :parent     nil
                                     :attributes {:system/xs-count (count xs)}}]
     [state
      {:output [{:context context
                 :xs      (vec xs)}]}])))



(defn create-flow
  "Returns a core.async.flow that takes input from `input-chan` and forms
   batches of a minimum size before forwarding to update `tail` and `mru`
   atoms."
  [{:keys [input-chan]}]
  (flow/create-flow
   {:procs {:update-tail {:proc (flow/process #'update-tail)
                          :args {:len 10}}
            :update-mru  {:proc (flow/process #'update-mru)
                          :args {:len 10}}
            :batch-tail  {:proc (flow/process #'batch)
                          :args {:size 5}}
            :batch-mru   {:proc (flow/process #'batch)
                          :args {:size 2}}
            :ingest      {:proc (flow/process #'ingest)
                          :args {::flow/in-ports {:input input-chan}}}}
    :conns [[[:batch-tail :output] [:update-tail :input]] ;
            [[:batch-mru :output] [:update-mru :input]]   ;
            [[:ingest :output] [:batch-tail :input]]      ;
            [[:ingest :output] [:batch-mru :input]]]}))



(comment
  (def ingest-chan
    (async/chan 10))
  (def fl
    (create-flow {:input-chan ingest-chan}))
  (def flow-chans
    (flow/start fl))
  (flow/resume fl)
  (async/>!! ingest-chan ["apple" "banana" "apple" "cherry" "apple" "doughnut"])
  (async/>!! ingest-chan ["apple" "banana"])
  (async/>!! ingest-chan []) ; flush buffers
  (deref tail)
  (deref mru)
  ;
)