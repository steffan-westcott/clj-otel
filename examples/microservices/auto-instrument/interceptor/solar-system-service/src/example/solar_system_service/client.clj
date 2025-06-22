(ns example.solar-system-service.client
  "HTTP client components, used to make HTTP requests to other microservices."
  (:require [hato.client :as hc]))


(defn client
  "Returns an HTTP client."
  []
  (hc/build-http-client {}))