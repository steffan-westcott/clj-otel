(ns example.puzzle-service.sync.requests
  "Requests to other microservices, synchronous implementation."
  (:require [example.puzzle-service.env :refer [config]]
            [hato.client :as client]
            [reitit.ring :as ring]
            [steffan-westcott.clj-otel.api.trace.http :as trace-http]
            [steffan-westcott.clj-otel.api.trace.span :as span]
            [steffan-westcott.clj-otel.context :as context]))


(defn- client-request
  "Perform a synchronous HTTP request using `hato`."
  [client request]

  (let [request (conj request
                      {:throw-exceptions false
                       :http-client      client})]

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



(defn get-random-word
  "Get a random word string of the requested type."
  [{:keys [client]} word-type]
  (let [endpoint (get-in config [:endpoints :random-word-service])
        response (client-request client
                                 {:method       :get
                                  :url          (str endpoint "/random-word")
                                  :query-params {:type (name word-type)}
                                  :accept       :json
                                  :as           :json})
        {:keys [status body]} response]
    (if (= 200 status)
      (:word body)
      (throw (ex-info "Unexpected HTTP response"
                      {:type          ::ring/response
                       :response      {:status status
                                       :body   {:error "Unexpected HTTP response"}}
                       :service/error :service.errors/unexpected-http-response})))))
