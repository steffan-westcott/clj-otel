(ns steffan-westcott.otel.exporter.zipkin
  "Span data exporter to Zipkin."
  (:require [steffan-westcott.otel.util :as util])
  (:import (io.opentelemetry.exporter.zipkin ZipkinSpanExporter)))

(defn span-exporter
  "Returns a span exporter that sends span data using the
  [`io.zipkin.reporter2:zipkin-reporter`](https://github.com/openzipkin/zipkin-reporter-java)
  library. Takes an option map as follows:

  | key           | description |
  |---------------|-------------|
  |`:endpoint`    | With the default sender, Zipkin endpoint e.g. `\"http://zipkinhost:9411/api/v2/spans\"` (default: `\"http://localhost:9411/api/v2/spans\"`).
  |`:read-timeout`| With the default sender, maximum time to wait for export of a batch of spans. Value is either a [[Duration]] or a vector `[amount ^TimeUnit unit]` (default: 10s).
  |`:sender`      | [[zipkin2.reporter.Sender]] used to send span data (default: [[OkHttpSender]] instance with `:endpoint` and `:read-timeout` config).
  |`:encoder`     | [[zipkin2.codec.BytesEncoder]] Format used to send span data (default: `SpanBytesEncoder/JSON_V2`)."
  ([]
   (span-exporter {}))
  ([{:keys [endpoint read-timeout sender encoder]}]
   (let [builder (cond-> (ZipkinSpanExporter/builder)
                         endpoint (.setEndpoint endpoint)
                         read-timeout (.setReadTimeout (util/duration read-timeout))
                         sender (.setSender sender)
                         encoder (.setEncoder encoder))]
     (.build builder))))
