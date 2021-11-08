(ns example.auto-instrument-agent.interceptor.planet-service
  "Example application demonstrating using `clj-otel` to add telemetry to a
  synchronous Pedestal HTTP service that is run with the OpenTelemetry
  instrumentation agent."
  (:require [steffan-westcott.otel.api.trace.span :as span]
            [steffan-westcott.otel.api.trace.http :as trace-http]
            [io.pedestal.http :as http]
            [io.pedestal.http.route :as route]
            [ring.util.response :as response]
            [clojure.string :as str]))

(def planet-metrics
  {:mercury {:diameter 4879 :gravity 3.7}
   :venus   {:diameter 12104 :gravity 8.9}
   :earth   {:diameter 12756 :gravity 9.8}
   :mars    {:diameter 6792 :gravity 3.7}
   :jupiter {:diameter 142984 :gravity 23.1}
   :saturn  {:diameter 120536 :gravity nil}                 ; missing gravity data
   :uranus  {:diameter 51118 :gravity 8.7}
   :neptune {:diameter 49528 :gravity 11.0}
   :pluto   {:diameter 2370 :gravity 0.7}})

(defn planet-metric
  "Returns a specific metric value for a planet."
  [planet metric]

  ;; Wrap the synchronous body in a new internal span.
  (span/with-span! {:name       "Fetching data"
                    :attributes {:the-planet planet :the-metric metric}}

    (Thread/sleep 50)
    (let [path [planet metric]]

      ;; Add an event to the current span with some data attached.
      (span/add-span-data! {:event {:name       "Processed query path"
                                    :attributes {:query-path path}}})

      (if-let [result (get-in planet-metrics path)]
        result

        ;; Simulate an intermittent runtime exception when attempt is made
        ;; to retrieve Saturn's gravity data.
        ;; An uncaught exception leaving a span's scope is reported as an
        ;; exception event and the span status description is set to the
        ;; exception triage summary.
        (throw (ex-info "Failed to retrieve metric" {:metric metric}))))))


(defn get-planet-metric-handler
  "Synchronous handler for 'GET /planets/:planet/:metric' request. Returns an
  HTTP response containing the requested metric value for a planet."
  [{:keys [path-params]}]
  (let [{:keys [planet metric]} path-params
        planet (keyword planet)
        metric (keyword metric)]

    ;; Add attributes describing matched route to server span.
    (trace-http/add-route-data! "/planets/:planet/:metric")

    ;; Simulate a client error when requesting data on Pluto.
    (if (= planet :pluto)
      (response/bad-request "Pluto is not a full planet")
      (response/response (str (planet-metric planet metric))))))


(def routes
  "Route maps for the service."
  (route/expand-routes
    [[[
       ;; Wrap request handling of all routes. As this application is run with
       ;; the OpenTelemetry instrumentation agent, a server span will be
       ;; provided by the agent and there is no need to create another one.
       "/" (trace-http/server-span-interceptors {:create-span? false
                                                 :server-name  "planet"})

       ["/planets/:planet/:metric"
        ^:constraints {:planet (re-pattern (str/join "|" (map name (keys planet-metrics))))
                       :metric #"diameter|gravity"}
        {:get 'get-planet-metric-handler}]]]]))


(def service-map
  {::http/routes routes
   ::http/type   :jetty
   ::http/port   8081
   ::http/join?  false})


(defn init-tracer
  "Set default tracer used when manually creating spans."
  []
  (let [tracer (span/get-tracer {:name "planet-service" :version "1.0.0"})]
    (span/set-default-tracer! tracer)))


;;;;;;;;;;;;;

(init-tracer)
(defonce server (http/start (http/create-server service-map)))
