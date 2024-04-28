(ns example.common.load-gen.client
  "HTTP client components, used to make HTTP requests."
  (:require [clj-http.conn-mgr :as conn]
            [clj-http.core :as http-core]))


(defn connection-manager
  "Returns an asynchronous pooling connection manager."
  []
  (conn/make-reusable-async-conn-manager {}))



(defn stop-connection-manager
  "Stops the given connection manager."
  [conn-mgr]
  (conn/shutdown-manager conn-mgr))



(defn client
  "Returns an asynchronous HTTP client."
  [conn-mgr]
  (http-core/build-async-http-client {} conn-mgr))
