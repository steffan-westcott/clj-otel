(ns example.solar-system-service.repl
  "Functions to operate and exercise the running service at the REPL."
  (:require [org.httpkit.client :as client]))


(defn process-response
  "Returns the status and body of a response, or client error."
  [{:keys [status body error]}]
  (or error
      {:status status
       :body   body}))


#_{:clj-kondo/ignore [:unresolved-var]}
(defn do-get-request
  "Make a GET request to the running system."
  ([path]
   (do-get-request path {}))
  ([path opts]
   (process-response @(client/get (str "http://localhost:8080" path) (assoc opts :as :text)))))


(defn get-ping
  "Request the running system for a ping health check."
  []
  (do-get-request "/ping"))


(defn get-statistics
  "Request the running system for statistics on the given planet."
  [planet]
  (do-get-request "/statistics"
                  {:query-params {"planet" (name planet)}}))


(defn unknown-request
  "Make an unknown request to the running system."
  []
  (do-get-request "/baz"))


(comment
  (get-ping)
  (get-statistics :earth)
  (get-statistics :pluto) ; 400 response
  (get-statistics :saturn) ; 500 response
  (unknown-request) ; 404 response
  ;
)

