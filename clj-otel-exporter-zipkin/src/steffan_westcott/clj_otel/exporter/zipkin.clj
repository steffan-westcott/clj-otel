(ns steffan-westcott.clj-otel.exporter.zipkin
  "Span data exporter to Zipkin."
  (:require [steffan-westcott.clj-otel.util :as util])
  (:import (io.opentelemetry.exporter.zipkin ZipkinSpanExporter)
           (java.util.function Supplier)))

(defn span-exporter
  "Returns a span exporter that sends span data using the
  [`io.zipkin.reporter2:zipkin-reporter`](https://github.com/openzipkin/zipkin-reporter-java)
  library. May take an option map as follows:

  | key                   | description |
  |-----------------------|-------------|
  |`:endpoint`            | With the default sender, Zipkin endpoint e.g. `\"http://zipkinhost:9411/api/v2/spans\"` (default: `\"http://localhost:9411/api/v2/spans\"`).
  |`:read-timeout`        | With the default sender, maximum time to wait for export of a batch of spans. Value is either a `Duration` or a vector `[amount ^TimeUnit unit]` (default: 10s).
  |`:sender`              | `zipkin2.reporter.Sender` used to send span data (default: `OkHttpSender` instance with `:endpoint` and `:read-timeout` config).
  |`:encoder`             | `zipkin2.codec.BytesEncoder` Format used to send span data (default: `SpanBytesEncoder/JSON_V2`).
  |`:local-ip-address-fn` | 0-arg function that returns `nil` or `InetAddress` of local Zipkin endpoint (default: fn that returns local IP address captured when exporter created).
  |`:compression`         | Method used to compress payloads. Value is string `\"gzip\"` or `\"none\"` (default: `\"gzip\"`)."
  ([]
   (span-exporter {}))
  ([{:keys [endpoint read-timeout sender encoder local-ip-address-fn compression]}]
   (let [builder (cond-> (ZipkinSpanExporter/builder)
                   endpoint            (.setEndpoint endpoint)
                   read-timeout        (.setReadTimeout (util/duration read-timeout))
                   sender              (.setSender sender)
                   encoder             (.setEncoder encoder)
                   local-ip-address-fn (.setLocalIpAddressSupplier (reify
                                                                    Supplier
                                                                      (get [_]
                                                                        (local-ip-address-fn))))
                   compression         (.setCompression compression))]
     (.build builder))))
