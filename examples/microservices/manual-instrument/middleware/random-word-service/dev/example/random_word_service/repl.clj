(ns example.random-word-service.repl
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
   (process-response @(client/get (str "http://localhost:8081" path) (assoc opts :as :text)))))


(defn get-ping
  "Request the running system for a ping health check."
  []
  (do-get-request "/ping"))


(defn get-random-word
  "Request the running system for a random word of the given type."
  [word-type]
  (do-get-request "/random-word"
                  {:query-params {"type" (name word-type)}}))


(defn unknown-request
  "Make an unknown request to the running system."
  []
  (do-get-request "/foo"))


(comment
  (get-ping)
  (get-random-word :noun)
  (get-random-word :bogus) ; 400 response
  (get-random-word :fault) ; 500 response
  (unknown-request) ; 404 response
  ;
)
