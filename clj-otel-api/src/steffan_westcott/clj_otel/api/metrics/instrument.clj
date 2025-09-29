(ns steffan-westcott.clj-otel.api.metrics.instrument
  "Functions for creating instruments and recording measurements."
  (:require [steffan-westcott.clj-otel.api.attributes :as attr]
            [steffan-westcott.clj-otel.api.otel :as otel]
            [steffan-westcott.clj-otel.config :refer [config]]
            [steffan-westcott.clj-otel.context :as context])
  (:import (io.opentelemetry.api OpenTelemetry)
           (io.opentelemetry.api.metrics DoubleCounter
                                         DoubleCounterBuilder
                                         DoubleGauge
                                         DoubleGaugeBuilder
                                         DoubleHistogram
                                         DoubleHistogramBuilder
                                         DoubleUpDownCounter
                                         DoubleUpDownCounterBuilder
                                         LongCounter
                                         LongCounterBuilder
                                         LongGauge
                                         LongGaugeBuilder
                                         LongHistogram
                                         LongHistogramBuilder
                                         LongUpDownCounter
                                         LongUpDownCounterBuilder
                                         Meter
                                         ObservableDoubleMeasurement
                                         ObservableLongMeasurement)
           (java.util.function Consumer)))

(def ^:private default-library
  (get-in config [:defaults :instrumentation-library]))

(defn get-meter
  "Builds and returns a `io.opentelemetry.api.metrics.Meter` instance. May take
   an option map as follows:

   | key             | description |
   |-----------------|-------------|
   |`:name`          | Name of the *instrumentation* library, not the *instrumented* library e.g. `\"io.opentelemetry.contrib.mongodb\"` (default: See `config.edn` resource file).
   |`:version`       | Instrumentation library version e.g. `\"1.0.0\"` (default: See `config.edn` resource file).
   |`:schema-url`    | URL of OpenTelemetry schema used by this instrumentation library (default: See `config.edn` resource file).
   |`:open-telemetry`| `OpenTelemetry` instance to get meter from (default: default `OpenTelemetry` instance)."
  (^Meter []
   (get-meter {}))
  (^Meter
   [{:keys [name version schema-url open-telemetry]
     :or   {name       (:name default-library)
            version    (:version default-library)
            schema-url (:schema-url default-library)}}]
   (let [^OpenTelemetry otel (or open-telemetry (otel/get-default-otel!))
         builder (cond-> (.meterBuilder otel name)
                   version    (.setInstrumentationVersion version)
                   schema-url (.setSchemaUrl schema-url))]
     (.build builder))))

(defonce ^:private default-meter
  (atom nil))

(defn set-default-meter!
  "Sets the default `io.opentelemetry.api.metrics.Meter` instance used when
   creating instruments. Returns `meter`. See also [[get-meter]]."
  ^Meter [meter]
  (reset! default-meter meter))

(defn get-default-meter!
  "Returns the default meter if not nil. Otherwise, gets a meter using
   defaults and sets this as the default meter."
  ^Meter []
  (swap! default-meter #(or % (get-meter))))

(defprotocol Counter
  "Protocol for instruments of type `:counter` or `:up-down-counter` that take
   measurements synchronously."
  (add! [counter delta]
   "Synchronously adds a delta to `counter`. `delta` is an option map as
    follows:

    | key         | description |
    |-------------|-------------|
    |`:context`   | Context to associate with delta (default: bound or current context).
    |`:value`     | `long` or `double` value to add to `counter` (required). Must not be negative for `:counter` instruments.
    |`:attributes`| Map of attributes to attach to delta (default: no attributes)."))

(defmacro ^:private add*
  [counter delta]
  `(let [{:keys [~'context ~'value ~'attributes]
          :or   {~'context (context/dyn)}}
         ~delta]
     (.add ~counter ~'value (attr/->attributes ~'attributes) ~'context)))

(extend-protocol Counter
 LongCounter
   (add! [counter delta]
     (add* counter delta))
 LongUpDownCounter
   (add! [counter delta]
     (add* counter delta))
 DoubleCounter
   (add! [counter delta]
     (add* counter delta))
 DoubleUpDownCounter
   (add! [counter delta]
     (add* counter delta)))

(defprotocol Histogram
  "Protocol for instruments of type `:histogram`."
  (record! [histogram measurement]
   "Synchronously records a measurement in `histogram`. `measurement` is an
    option map as follows:

    | key         | description |
    |-------------|-------------|
    |`:context`   | Context to associate with measurement (default: bound or current context).
    |`:value`     | `long` or `double` value to record in `histogram` (required).
    |`:attributes`| Map of attributes to attach to measurement (default: no attributes)."))

(defmacro ^:private record*
  [histogram measurement]
  `(let [{:keys [~'context ~'value ~'attributes]
          :or   {~'context (context/dyn)}}
         ~measurement]
     (.record ~histogram ~'value (attr/->attributes ~'attributes) ~'context)))

(extend-protocol Histogram
 LongHistogram
   (record! [histogram measurement]
     (record* histogram measurement))
 DoubleHistogram
   (record! [histogram measurement]
     (record* histogram measurement)))

(defprotocol Gauge
  "Protocol for instruments of type `:gauge` that take measurements
   synchronously."
  (set! [gauge measurement]
   "Synchronously sets a measurement in `gauge`. `measurement` is an option map
    as follows:

     | key         | description |
     |-------------|-------------|
     |`:context`   | Context to associate with measurement (default: bound or current context).
     |`:value`     | `long` or `double` value to set in `gauge` (required).
     |`:attributes`| Map of attributes to attach to measurement (default: no attributes)."))

(defmacro ^:private set*
  [gauge measurement]
  `(let [{:keys [~'context ~'value ~'attributes]
          :or   {~'context (context/dyn)}}
         ~measurement]
     (.set ~gauge ~'value (attr/->attributes ~'attributes) ~'context)))

(extend-protocol Gauge
 LongGauge
   (set! [gauge measurement]
     (set* gauge measurement))
 DoubleGauge
   (set! [gauge measurement]
     (set* gauge measurement)))

(defmacro ^:private callback*
  [observe measurement-class]
  `(reify
    Consumer
      (~'accept
        [~'_ ~'measurement]
        (let [~(with-meta 'mmt {:tag measurement-class}) ~'measurement
              ~'obs (~observe)]
          (if (map? ~'obs)
            (let [{:keys [~'value ~'attributes]} ~'obs]
              (.record ~'mmt ~'value (attr/->attributes ~'attributes)))
            (doseq [{:keys [~'value ~'attributes]} ~'obs]
              (.record ~'mmt ~'value (attr/->attributes ~'attributes))))))))

(defn- callback-long
  [observe]
  (callback* observe ObservableLongMeasurement))

(defn- callback-double
  [observe]
  (callback* observe ObservableDoubleMeasurement))

(defprotocol ^:no-doc Builder
  (set-unit [builder unit])
  (set-description [builder description])
  (set-type [builder type])
  (set-explicit-bucket-boundaries-advice [builder boundaries])
  (build [builder]
         [builder observe]))

#_{:clj-kondo/ignore [:missing-protocol-method]}
(extend-protocol Builder
 LongCounterBuilder
   (set-unit [builder unit]
     (.setUnit builder unit))
   (set-description [builder description]
     (.setDescription builder description))
   (set-type [builder type]
     (case type
       :long   builder
       :double (.ofDoubles builder)))
   (build
     ([builder] (.build builder))
     ([builder observe] (.buildWithCallback builder (callback-long observe))))
 LongUpDownCounterBuilder
   (set-unit [builder unit]
     (.setUnit builder unit))
   (set-description [builder description]
     (.setDescription builder description))
   (set-type [builder type]
     (case type
       :long   builder
       :double (.ofDoubles builder)))
   (build
     ([builder] (.build builder))
     ([builder observe] (.buildWithCallback builder (callback-long observe))))
 DoubleHistogramBuilder
   (set-unit [builder unit]
     (.setUnit builder unit))
   (set-description [builder description]
     (.setDescription builder description))
   (set-type [builder type]
     (case type
       :long   (.ofLongs builder)
       :double builder))
   (set-explicit-bucket-boundaries-advice [builder boundaries]
     (.setExplicitBucketBoundariesAdvice builder (vec boundaries)))
   (build
     ([builder] (.build builder)))
 DoubleGaugeBuilder
   (set-unit [builder unit]
     (.setUnit builder unit))
   (set-description [builder description]
     (.setDescription builder description))
   (set-type [builder type]
     (case type
       :long   (.ofLongs builder)
       :double builder))
   (build
     ([builder] (.build builder))
     ([builder observe] (.buildWithCallback builder (callback-double observe))))
 DoubleCounterBuilder
   (build
     ([builder] (.build builder))
     ([builder observe] (.buildWithCallback builder (callback-double observe))))
 DoubleUpDownCounterBuilder
   (build
     ([builder] (.build builder))
     ([builder observe] (.buildWithCallback builder (callback-double observe))))
 LongHistogramBuilder
   (set-explicit-bucket-boundaries-advice [builder boundaries]
     (.setExplicitBucketBoundariesAdvice builder (vec boundaries)))
   (build
     ([builder] (.build builder)))
 LongGaugeBuilder
   (build
     ([builder] (.build builder))
     ([builder observe] (.buildWithCallback builder (callback-long observe)))))

(defn instrument
  "Builds an instrument for taking measurements either synchronously or
   asynchronously (but not both).

   The first parameter `opts` is an options map as follows:

   | key                                | description |
   |------------------------------------|-------------|
   |`:meter`                            | `io.opentelemetry.api.metrics.Meter` used to create the instrument (default: default meter, as set by [[set-default-meter!]]; if no default meter has been set, one will be set with default config).
   |`:name`                             | Name of the instrument. Must be 63 or fewer characters including alphanumeric, `_`, `.`, `-`, and start with a letter (required).
   |`:instrument-type`                  | Type of instrument, one of `:counter`, `:up-down-counter`, `:histogram` or `:gauge` (required).
   |`:measurement-type`                 | Type of measurement value, either `:long` or `:double` (default: `:long`).
   |`:unit`                             | String describing the unit of measurement (default: no specified unit).
   |`:description`                      | String describing the instrument (default: no description).
   |`:explicit-bucket-boundaries-advice`| Seq of increasing longs or doubles, recommended bucket boundaries when building a histogram (default: no advice given).

   The 1-arity form of [[instrument]] is for building instruments that take
   measurements synchronously. Counter, up-down counter, gauge and histogram
   instruments are supported. The built instrument is returned, and measurements
   are made with the `add!` (counter/up-down counter), `set!` (gauge) and
   `record!` (histogram) functions.

   The 2-arity form of [[instrument]] is for building instruments that take
   measurements asynchronously. Counter, up-down counter and gauge instruments
   are supported. The second parameter `observe` is a 0-arity function that will
   be evaluated periodically to take measurements. To stop, evaluate `.close` on
   the `AutoCloseable` that [[instrument]] returns.

   `observe` should return a single map, or a sequence of maps, as follows:

   | key         | description |
   |-------------|-------------|
   |`:value`     | `long` or `double` value to add or record (required).
   |`:attributes`| Map of attributes to attach to measurement (default: no attributes)."
  ([opts]
   (instrument opts nil))
  ([{:keys [^Meter meter name instrument-type measurement-type unit description
            explicit-bucket-boundaries-advice]
     :or   {measurement-type :long}} observe]
   (let [meter   (or meter (get-default-meter!))
         builder (cond-> (case instrument-type
                           :counter         (.counterBuilder meter name)
                           :up-down-counter (.upDownCounterBuilder meter name)
                           :histogram       (.histogramBuilder meter name)
                           :gauge           (.gaugeBuilder meter name))
                   unit        (set-unit unit)
                   description (set-description description)
                   :always     (set-type measurement-type)
                   explicit-bucket-boundaries-advice (set-explicit-bucket-boundaries-advice
                                                      explicit-bucket-boundaries-advice))]
     (if observe
       (build builder observe)
       (build builder)))))
