(ns example.sentence-summary-service.repl
  "Functions to operate and exercise the running service at the REPL."
  (:require [clojure.data.json :as json]
            [org.httpkit.client :as client]))


(defn process-response
  "Returns the status and decoded JSON body of a response, or client error."
  [{:keys [status body error]}]
  (or error
      {:status status
       :body   (cond-> body
                 (= 200 status) (json/read-str {:eof-error? false
                                                :key-fn     keyword}))}))


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


(defn get-summary
  "Request the running system for a summary of the given sentence."
  [sentence]
  (do-get-request "/summary"
                  {:query-params {"sentence" sentence}}))


(defn unknown-request
  "Make an unknown request to the running system."
  []
  (do-get-request "/foo"))


(comment
  (get-ping)
  (get-summary "An idiomatic Clojure API for adding telemetry")
  (get-summary "This causes a problem") ; 400 response
  (get-summary "Server goes boom") ; 500 response
  (unknown-request) ; 404 response
  ;
)
