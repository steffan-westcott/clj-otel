(ns example.average-service.sync.requests
  "Requests to other microservices, synchronous implementation."
  (:require [clj-http.client :as client]
            [clojure.string :as str]
            [steffan-westcott.clj-otel.api.trace.http :as trace-http]
            [steffan-westcott.clj-otel.api.trace.span :as span]
            [steffan-westcott.clj-otel.context :as context]))


(defn- client-request
  "Perform a synchronous HTTP request using `clj-http`."
  [{:keys [conn-mgr client]} request]

  (let [request (conj request
                      {:throw-exceptions   false
                       :connection-manager conn-mgr
                       :http-client        client})]

    ;; Wrap the synchronous body in a new client span.
    (span/with-span! (trace-http/client-span-opts request)

      (let [;; Propagate context containing client span to remote
            ;; server by injecting headers. This enables span
            ;; correlation to make distributed traces.
            request' (update request :headers merge (context/->headers))

            response (try
                       (client/request request')
                       (catch Throwable e
                         (trace-http/add-client-span-response-data!
                          {:io.opentelemetry.api.trace.span.attrs/error-type e})
                         (throw e)))]

        ;; Add HTTP response data to the client span.
        (trace-http/add-client-span-response-data! response)

        response))))



(defn get-sum
  "Get the sum of the nums."
  [{:keys [config]
    :as   components} nums]
  (let [endpoint (get-in config [:endpoints :sum-service])
        response (client-request components
                                 {:method       :get
                                  :url          (str endpoint "/sum")
                                  :query-params {"nums" (str/join "," nums)}
                                  :accept       :json
                                  :as           :json})
        {:keys [status body]} response]
    (if (= 200 status)
      (:sum body)
      (throw (ex-info (str status " HTTP response")
                      {:http.response/status status
                       :service/error        :service.errors/unexpected-http-response})))))
