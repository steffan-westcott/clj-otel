(ns example.sentence-summary-service.client
  "HTTP client components, used to make HTTP requests to other microservices."
  (:require [clj-http.conn-mgr :as conn]
            [clj-http.core :as http-core]))


(defn- async?
  [config]
  (case (:server-impl config)
    "sync"           false
    "bound-async"    true
    "explicit-async" true))



(defn connection-manager
  "Returns a (a)synchronous pooling connection manager."
  [config]
  (if (async? config)
    (conn/make-reusable-async-conn-manager {})
    (conn/make-reusable-conn-manager {})))



(defn stop-connection-manager
  "Stops the given connection manager."
  [conn-mgr]
  (conn/shutdown-manager conn-mgr))



(defn client
  "Returns a (a)synchronous HTTP client."
  [config conn-mgr]
  (if (async? config)
    (http-core/build-async-http-client {} conn-mgr)
    (http-core/build-http-client {} false conn-mgr)))
