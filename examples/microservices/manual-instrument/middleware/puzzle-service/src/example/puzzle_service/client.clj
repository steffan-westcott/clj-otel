(ns example.puzzle-service.client
  "HTTP client components, used to make HTTP requests to other microservices."
  (:require [clj-http.conn-mgr :as conn]
            [clj-http.core :as http-core]
            [example.puzzle-service.env :refer [config]]))


(defn- async?
  []
  (case (:server-impl config)
    "sync"           false
    "bound-async"    true
    "explicit-async" true))



(defn connection-manager
  "Returns a (a)synchronous pooling connection manager."
  []
  (if (async?)
    (conn/make-reusable-async-conn-manager {})
    (conn/make-reusable-conn-manager {})))



(defn stop-connection-manager
  "Stops the given connection manager."
  [conn-mgr]
  (conn/shutdown-manager conn-mgr))



(defn client
  "Returns a (a)synchronous HTTP client."
  [conn-mgr]
  (if (async?)
    (http-core/build-async-http-client {} conn-mgr)
    (http-core/build-http-client {} false conn-mgr)))
