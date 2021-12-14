(ns example.manual-instrument.middleware.random-word-service
  "Example application demonstrating using `clj-otel` to add telemetry to a
  synchronous Ring HTTP service that is run without the OpenTelemetry
  instrumentation agent."
  (:require [example.common-utils.middleware :as middleware]
            [ring.adapter.jetty :as jetty]
            [ring.middleware.params :as params]
            [ring.util.response :as response]
            [steffan-westcott.otel.api.trace.http :as trace-http]
            [steffan-westcott.otel.api.trace.span :as span]))

(def words
  "Map of word types and collections of random words of that type."
  {:noun      ["amusement" "bat" "cellar" "engine" "flesh" "frogs" "hearing" "record"]
   :verb      ["afford" "behave" "ignite" "justify" "race" "sprout" "strain" "wake"]
   :adjective ["cultured" "glorious" "grumpy" "handy" "kind" "lush" "mixed" "shut"]})



(defn random-word
  "Gets a random word of the requested type."
  [word-type]

  ;; Wrap the synchronous body in a new internal span.
  (span/with-span! {:name "Generating word" :attributes {:word-type word-type}}

    (Thread/sleep (+ 10 (rand-int 80)))
    (let [candidates (or (get words word-type)
                         (throw (ex-info "Unknown word type" {:status 400
                                                              :error  ::unknown-word-type
                                                              ::type  word-type})))
          word (rand-nth candidates)]

      ;; Add more attributes to the internal span
      (span/add-span-data! {:attributes {:generated-word word}})

      word)))



(defn get-random-word-handler
  "Synchronous Ring handler for 'GET /random-word' request. Returns an HTTP
  response containing a random word of the requested type."
  [{:keys [query-params]}]

  ; Add attributes describing matched route to server span
  (trace-http/add-route-data! "/random-word")

  (let [type (keyword (get query-params "type"))
        result (random-word type)]
    (Thread/sleep (+ 20 (rand-int 20)))
    (response/response (str result))))



(defn handler
  "Synchronous Ring handler for all requests."
  [{:keys [request-method uri] :as request}]
  (case [request-method uri]
    [:get "/random-word"] (get-random-word-handler request)
    (response/not-found "Not found")))



(def service
  "Ring handler with middleware applied."
  (-> handler
      params/wrap-params
      middleware/wrap-exception

      ;; Wrap request handling of all routes. As this application is not run
      ;; with the OpenTelemetry instrumentation agent, create a server span
      ;; for each request.
      (trace-http/wrap-server-span {:create-span? true :server-name "random"})))



(defonce ^{:doc "random-word-service server instance"} server
         (jetty/run-jetty #'service {:port 8081 :join? false}))
