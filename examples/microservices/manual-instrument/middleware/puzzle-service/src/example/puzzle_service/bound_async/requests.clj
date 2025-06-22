(ns example.puzzle-service.bound-async.requests
  "Requests to other microservices, bound async implementation."
  (:require [clojure.core.async :as async]
            [example.common.core-async.utils :as async']
            [example.puzzle-service.env :refer [config]]
            [hato.client :as client]
            [reitit.ring :as ring]
            [steffan-westcott.clj-otel.api.trace.http :as trace-http]
            [steffan-westcott.clj-otel.api.trace.span :as span]
            [steffan-westcott.clj-otel.context :as context]))


(defn- client-request
  "Make an asynchronous HTTP request using `hato`."
  [client request respond raise]

  (let [request (conj request
                      {:async?           true
                       :throw-exceptions false
                       :http-client      client})]

    ;; Manually create a client span. The client span is ended when either a
    ;; response or exception is returned.
    (span/async-bound-span (trace-http/client-span-opts request)
                           (fn [respond* raise*]

                             (let [;; Propagate context containing client span to remote
                                   ;; server by injecting headers. This enables span
                                   ;; correlation to make distributed traces.
                                   request' (update request :headers merge (context/->headers))]

                               ;; Use `bound-fn` to ensure respond/raise fns use bound context
                               (client/request
                                request'
                                (bound-fn [response]

                                  ;; Add HTTP response data to the client span.
                                  (trace-http/add-client-span-response-data! response)

                                  (respond* response))
                                (bound-fn [e]

                                  ;; Add error information to the client span.
                                  (trace-http/add-client-span-response-data!
                                   {:io.opentelemetry.api.trace.span.attrs/error-type e})

                                  (raise* e)))))
                           respond
                           raise)))



(defn- <client-request
  "Make an asynchronous HTTP request and return a channel of the response."
  [client request]
  (let [<ch    (async/chan)
        put-ch #(async/put! <ch %)]
    (client-request client request put-ch put-ch)
    <ch))



(defn <get-random-word
  "Get a random word string of the requested type and return a channel of the
   word."
  [{:keys [client]} word-type]
  (let [endpoint  (get-in config [:endpoints :random-word-service])
        <response (<client-request client
                                   {:method       :get
                                    :url          (str endpoint "/random-word")
                                    :query-params {:type (name word-type)}
                                    :accept       :json
                                    :as           :json})]
    (async'/go-try
      (let [{:keys [status body]} (async'/<? <response)]
        (if (= 200 status)
          (:word body)
          (throw (ex-info "Unexpected HTTP response"
                          {:type          ::ring/response
                           :response      {:status status
                                           :body   {:error "Unexpected HTTP response"}}
                           :service/error :service.errors/unexpected-http-response})))))))
