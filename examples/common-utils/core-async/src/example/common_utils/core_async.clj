(ns example.common-utils.core-async
  (:require [clojure.core.async :as async]
            [steffan-westcott.otel.api.trace.span :as span]))


(defn throwable?
  "Returns `true` if `x` is a throwable exception, `false` otherwise."
  [x]
  (instance? Throwable x))



(defn maybe-throw
  "Returns `x`, but throws if `x` is an exception."
  [x]
  (if (throwable? x) (throw x) x))



(defmacro <?
  "Same as `<!`, but throws any exception value taken from the channel."
  [ch]
  `(maybe-throw (async/<! ~ch)))



(defn <concat
  "Returns a channel which has all values from first input channel, then all
  from the next input channel and so on. The returned channel is closed after
  all input channels have been drained and closed."
  [chs]
  (let [res (async/chan)]
    (async/go-loop [chs chs]
      (if (seq chs)
        (if-some [v (async/<! (first chs))]
          (do
            (async/>! res v)
            (recur chs))
          (recur (rest chs)))
        (async/close! res)))
    res))



(defn close-and-drain!!
  "Ensure a channel is closed and drained of values."
  [ch]
  (async/close! ch)
  (loop []
    (when (some? (async/<!! ch))
      (recur))))



(defmacro catch-all [& body]
  `(try
     ~@body
     (catch Throwable e#
       e#)))



(defmacro go-try
  "Same as `go` but catch any exceptions and return as channel value."
  [& body]
  `(async/go (catch-all ~@body)))



(defn <reduce??
  "Same as async/reduce but immediately returns any exception taken from
  channel 'ch' (or thrown during evaluation of `f`) as the reduced value.
  Returns a channel containing a single reduced value."
  [f init ch]
  (async/go
    (catch-all
      (loop [ret init]
        (let [v (<? ch)]
          (if (nil? v)
            ret
            (let [ret' (f ret v)]
              (if (reduced? ret')
                @ret'
                (recur ret')))))))))



(defn <into??
  "Same as async/into but returned channel will contain a single exception
  value instead of the collection if an exception is taken from channel 'ch'."
  [coll ch]
  (<reduce?? conj coll ch))



(defn ch->respond-raise
  "Takes a single value from channel `<ch` and invokes a callback function
  `respond` or `raise`."
  [<ch respond raise]
  (async/take! <ch (fn [x]
                     (if (throwable? x)
                       (raise x)
                       (respond x)))))



(defmacro catch-response [context & body]
  `(try
     ~@body
     (catch Throwable e#

       ;; Add non-escaping exception to span as an event
       (span/add-exception! e# {:context ~context :escaping? false})

       {:body   (.getMessage e#)
        :status 500})))



(defmacro go-try-response
  "Same as `go` but channel return value is Pedestal interceptor context `ctx`
  with body return value assoc'ed as `:response`. Any exception is converted to
  a 500 Server Error response."
  [ctx & body]
  `(async/go
     (let [context# (:io.opentelemetry/server-span-context ~ctx)
           response# (catch-response context# ~@body)]
       (assoc ~ctx :response response#))))



;; Used by <with-span-binding
(defn <instrumented-pipe [context <src buf-size timeout respond raise]
  (let [<dest (async/chan buf-size)
        <timeout (async/timeout timeout)]

    ;; Implementation note: While active, the timeout channel `<timeout`
    ;; prevents the go loop becoming eligible for garbage collection if `<src`
    ;; and `<dest` are garbage collected. This guarantees that either `respond`
    ;; or `raise` are evaluated (before or on timeout) and thus the span is
    ;; always ended.

    (async/go-loop [timer-running? true v nil]
      (if (nil? v)
        (async/alt!
          <timeout (do
                     (raise (ex-info "Truncating span due to channel timeout" {::error :src-take-timeout}))
                     (recur false nil))
          <src ([x] (if (some? x)
                      (do
                        (when (and timer-running? (throwable? x))
                          (span/add-exception! x {:context context}))
                        (recur timer-running? x))
                      (do
                        (async/close! <dest)
                        (when timer-running?
                          (respond nil)))))
          :priority true)
        (async/alt!
          <timeout (do
                     (raise (ex-info "Truncating span due to channel timeout" {::error :dest-put-timeout}))
                     (recur false v))
          [[<dest v]] (recur timer-running? nil)
          :priority true)))
    <dest))



(defmacro <with-span-binding
  "Starts a new span, binds `context` to the context containing the span and
  evaluates `body` which should return a `<src` channel. The span is ended
  when a close operation on `<src` completes (after values on `<src` have been
  consumed) or `timeout` milliseconds have elapsed. Because spans must be ended
  before they are sent to the back end, the timeout guarantees the span will
  not be missing from the reported trace. If either `body` throws an exception
  or exception values are put on `<src`, exception events will be added to the
  span. `span-opts` is the same as for [[new-span!]]. Returns a `<dest` channel
  with buffer size `buf-size`, where values are taken from `<src` and placed on
  `<dest` irrespective of timeout. `<dest` will stop consuming and close when
  `<src` closes."
  [[context span-opts] timeout buf-size & body]
  `(span/async-span
     ~span-opts
     (fn [context# respond# raise#]
       (let [~context context#
             <src# (do ~@body)]
         (<instrumented-pipe context# <src# ~buf-size ~timeout respond# raise#)))
     identity
     identity))
