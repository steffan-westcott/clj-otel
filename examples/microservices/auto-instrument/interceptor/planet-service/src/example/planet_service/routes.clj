(ns example.planet-service.routes
  "HTTP routes"
  (:require [clojure.string :as str]
            [example.planet-service.app :as app]
            [ring.util.response :as response]
            [steffan-westcott.clj-otel.api.trace.span :as span]))


(defn- get-ping
  "Returns an empty response."
  [_]
  (response/response nil))



(defn- get-planet-statistic
  "Returns a response containing the requested planet statistic."
  [{:keys [components path-params]}]

  ;; Ensure uncaught exceptions are recorded before they are transformed
  (span/with-span! "Handling route"

    (let [{:keys [planet statistic]} path-params
          planet    (keyword planet)
          statistic (keyword statistic)]

      ;; Simulate a client error when requesting data on Pluto. Exception data is added as
      ;; attributes to the exception event by default.
      (if (= planet :pluto)
        (throw (ex-info "Pluto is not a full planet"
                        {:http.response/status 400
                         :service/error        :service.planet.errors/pluto-not-full-planet}))
        (response/response {:statistic (app/planet-statistic components planet statistic)})))))



(defn routes
  "Routes for the service."
  []
  #{["/ping" :get get-ping]
    ["/planets/:planet/:statistic" :get get-planet-statistic ;
     :constraints
     {:planet    (re-pattern (str/join "|" (map name (keys app/planet-statistics))))
      :statistic #"diameter|gravity"}]})
