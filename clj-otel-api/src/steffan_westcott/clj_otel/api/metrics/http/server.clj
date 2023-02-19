(ns steffan-westcott.clj-otel.api.metrics.http.server
  "Support for HTTP server metrics semantic conventions when not using the
  OpenTelemetry instrumentation agent."
  (:require [steffan-westcott.clj-otel.api.metrics.instrument :as instrument]
            [steffan-westcott.clj-otel.context :as context])
  (:import (io.opentelemetry.semconv.trace.attributes SemanticAttributes)))

(defn- now-millis!
  []
  (System/currentTimeMillis))

(defn- since-millis!
  [start-time]
  (- (now-millis!) start-time))

(defonce
 ^{:doc
   "Delay containing an up-down counter that records the number of
   concurrent HTTP requests that are currently in flight."}
 active-requests
 (delay (instrument/instrument
         {:name        "http.server.active_requests"
          :instrument-type :up-down-counter
          :unit        "{requests}"
          :description "The number of concurrent HTTP requests that are currently in flight"})))

(defonce
 ^{:doc
   "Delay containing a histogram that records the duration of inbound HTTP
   request processing."}
 request-duration
 (delay (instrument/instrument {:name        "http.server.duration"
                                :instrument-type :histogram
                                :measurement-type :double
                                :unit        "ms"
                                :description "The duration of inbound HTTP request processing"})))

(defonce
 ^{:doc "Delay containing a histogram that records the size of inbound
   HTTP request messages."}
 request-size
 (delay (instrument/instrument {:name        "http.server.request.size"
                                :instrument-type :histogram
                                :unit        "By"
                                :description "The size of inbound HTTP request messages"})))

;; We lack access to the size of each HTTP response
#_(defonce
   ^{:doc
     "Delay containing a histogram that records the size of outbound HTTP
  response messages."}
   response-size
   (delay (instrument/instrument {:name        "http.server.response.size"
                                  :instrument-type :histogram
                                  :unit        "By"
                                  :description "The size of HTTP response messages"})))

(defn- add-active-requests!
  ([value attrs] (add-active-requests! value attrs (context/current)))
  ([value attrs context]
   (instrument/add! @active-requests
                    {:value      value
                     :attributes attrs
                     :context    context})))

(defn- active-requests-attrs
  [server-request-attrs]
  (select-keys server-request-attrs
               [SemanticAttributes/HTTP_FLAVOR SemanticAttributes/HTTP_METHOD
                SemanticAttributes/HTTP_SCHEME SemanticAttributes/NET_HOST_NAME]))

(defn- request-duration-or-size-attrs
  [server-request-attrs status]
  (-> (select-keys server-request-attrs
                   [SemanticAttributes/HTTP_FLAVOR SemanticAttributes/HTTP_METHOD
                    SemanticAttributes/HTTP_ROUTE SemanticAttributes/HTTP_SCHEME
                    SemanticAttributes/NET_HOST_NAME SemanticAttributes/NET_HOST_PORT])
      (assoc SemanticAttributes/HTTP_STATUS_CODE status)))

(defn- record-duration!
  ([start-time server-request-attrs status]
   (record-duration! start-time server-request-attrs status (context/current)))
  ([start-time server-request-attrs status context]
   (instrument/record! @request-duration
                       {:value      (since-millis! start-time)
                        :attributes (request-duration-or-size-attrs server-request-attrs status)
                        :context    context})))

(defn- record-size!
  ([server-request-attrs status] (record-size! server-request-attrs status (context/current)))
  ([server-request-attrs status context]
   (when-let [size (get server-request-attrs SemanticAttributes/HTTP_REQUEST_CONTENT_LENGTH)]
     (instrument/record! @request-size
                         {:value      size
                          :attributes (request-duration-or-size-attrs server-request-attrs status)
                          :context    context}))))

(defn wrap-active-requests
  "Ring middleware to add support for metric `http.server.active_requests`.
  This middleware should not be used for applications run with the
  OpenTelemetry instrumentation agent."
  [handler]
  (fn
    ([{:keys [io.opentelemetry/server-request-attrs]
       :as   request}]
     (let [attrs (active-requests-attrs server-request-attrs)]
       (add-active-requests! 1 attrs)
       (try
         (handler request)
         (finally
           (add-active-requests! -1 attrs)))))
    ([{:keys [io.opentelemetry/server-request-attrs io.opentelemetry/server-span-context]
       :as   request} respond raise]
     (let [attrs (active-requests-attrs server-request-attrs)]
       (add-active-requests! 1 attrs server-span-context)
       (try
         (handler request
                  (fn [response]
                    (add-active-requests! -1 attrs server-span-context)
                    (respond response))
                  (fn [e]
                    (add-active-requests! -1 attrs server-span-context)
                    (raise e)))
         (catch Throwable e
           (add-active-requests! -1 attrs server-span-context)
           (raise e)))))))

(defn active-requests-interceptor
  "Pedestal interceptor to add support for metric
  `http.server.active_requests`. The interceptor should not be used for
  applications run with the OpenTelemetry instrumentation agent."
  []
  {:name  ::active-requests
   :enter (fn [{:io.opentelemetry/keys [server-request-attrs server-span-context]
                :as ctx}]
            (add-active-requests! 1
                                  (active-requests-attrs server-request-attrs)
                                  server-span-context)
            ctx)
   :leave (fn [{:io.opentelemetry/keys [server-request-attrs server-span-context]
                :as ctx}]
            (add-active-requests! -1
                                  (active-requests-attrs server-request-attrs)
                                  server-span-context)
            ctx)
   :error (fn [{:io.opentelemetry/keys [server-request-attrs server-span-context]
                :as ctx} e]
            (add-active-requests! -1
                                  (active-requests-attrs server-request-attrs)
                                  server-span-context)
            (assoc ctx :io.pedestal.interceptor.chain/error e))})

(defn wrap-request-duration
  "Ring middleware to add support for metric `http.server.duration`. This
  middleware should not be used for applications run with the OpenTelemetry
  instrumentation agent."
  [handler]
  (fn
    ([{:keys [io.opentelemetry/server-request-attrs]
       :as   request}]
     (let [start-time (now-millis!)]
       (try
         (let [response (handler request)]
           (record-duration! start-time server-request-attrs (:status response))
           response)
         (catch Throwable e
           (record-duration! start-time server-request-attrs 500)
           (throw e)))))
    ([{:keys [io.opentelemetry/server-request-attrs io.opentelemetry/server-span-context]
       :as   request} respond raise]
     (let [start-time (now-millis!)]
       (try
         (handler request
                  (fn [response]
                    (record-duration! start-time
                                      server-request-attrs
                                      (:status response)
                                      server-span-context)
                    (respond response))
                  (fn [e]
                    (record-duration! start-time
                                      server-request-attrs
                                      500
                                      server-span-context)
                    (raise e)))
         (catch Throwable e
           (record-duration! start-time server-request-attrs 500 server-span-context)
           (raise e)))))))

(defn request-duration-interceptor
  "Pedestal interceptor to add support for metric `http.server.duration`. This
  interceptor should not be used for applications run with the OpenTelemetry
  instrumentation agent."
  []
  {:name  ::request-duration
   :enter (fn [ctx]
            (assoc ctx ::start-time (now-millis!)))
   :leave (fn
            [{:io.opentelemetry/keys [server-request-attrs server-span-context]
              ::keys [start-time]
              :keys  [response]
              :as    ctx}]
            (record-duration! start-time
                              server-request-attrs
                              (:status response)
                              server-span-context)
            ctx)
   :error (fn [{:io.opentelemetry/keys [server-request-attrs server-span-context]
                ::keys [start-time]
                :as    ctx} e]
            (record-duration! start-time
                              server-request-attrs
                              500
                              server-span-context)
            (assoc ctx :io.pedestal.interceptor.chain/error e))})

(defn wrap-request-size
  "Ring middleware to add support for metric `http.server.request.size`. This
  middleware should not be used for applications run with the OpenTelemetry
  instrumentation agent."
  [handler]
  (fn
    ([{:keys [io.opentelemetry/server-request-attrs]
       :as   request}]
     (try
       (let [response (handler request)]
         (record-size! server-request-attrs (:status response))
         response)
       (catch Throwable e
         (record-size! server-request-attrs 500)
         (throw e))))
    ([{:keys [io.opentelemetry/server-request-attrs io.opentelemetry/server-span-context]
       :as   request} respond raise]
     (try
       (handler request
                (fn [response]
                  (record-size!
                   server-request-attrs
                   (:status response)
                   server-span-context)
                  (respond response))
                (fn [e]
                  (record-size!
                   server-request-attrs
                   500
                   server-span-context)
                  (raise e)))
       (catch Throwable e
         (record-size! server-request-attrs 500 server-span-context)
         (raise e))))))

(defn request-size-interceptor
  "Pedestal interceptor to add support for metric `http.server.request.size`.
  This interceptor should not be used for applications run with the
  OpenTelemetry instrumentation agent."
  []
  {:name  ::request-size
   :leave (fn [{:io.opentelemetry/keys [server-request-attrs server-span-context]
                :keys [response]
                :as   ctx}]
            (record-size!
             server-request-attrs
             (:status response)
             server-span-context)
            ctx)
   :error (fn [{:io.opentelemetry/keys [server-request-attrs server-span-context]
                :as ctx} e]
            (record-size!
             server-request-attrs
             500
             server-span-context)
            (assoc ctx :io.pedestal.interceptor.chain/error e))})


(defn wrap-metrics-by-route
  "Ring middleware that add support for HTTP server metrics which include the
  `http.route` attribute. This middleware should not be used for applications
  run with the OpenTelemetry instrumentation agent."
  [handler]
  (-> handler
      wrap-request-size
      wrap-request-duration))

(defn metrics-by-route-interceptors
  "Returns a vector of interceptors that add support for HTTP server metrics
  which include the `http.route` attribute. These interceptors should not be
  used for applications run with the OpenTelemetry instrumentation agent."
  []
  [(request-duration-interceptor) (request-size-interceptor)])
