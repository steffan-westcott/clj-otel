(ns example.common.load-gen.client
  "HTTP client components, used to make HTTP requests."
  (:require [hato.client :as hc]))


(defn client
  "Returns an HTTP client."
  []
  (hc/build-http-client {}))
