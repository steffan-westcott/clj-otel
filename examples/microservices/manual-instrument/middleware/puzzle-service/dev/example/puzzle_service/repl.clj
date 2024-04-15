(ns example.puzzle-service.repl
  "Functions to operate and exercise the running service at the REPL."
  (:require [clojure.data.json :as json]
            [clojure.string :as str]
            [org.httpkit.client :as client]))


(defn process-response
  "Returns the status and decoded JSON body of a response, or client error."
  [{:keys [status body error]}]
  (if error
    {:error error}
    {:status status
     :body   (and body
                  (json/read-str body
                                 {:eof-error? false
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


(defn get-puzzle
  "Request the running system for a puzzle with the given word types."
  [word-types]
  (do-get-request "/puzzle"
                  {:query-params {"types" (str/join "," (map name word-types))}
                   :headers      {"Accept" "application/json"}}))


(defn unknown-request
  "Make an unknown request to the running system."
  []
  (do-get-request "/foo"))


(comment
  (get-ping)
  (get-puzzle [:noun :verb :adjective :noun])
  (get-puzzle [:bogus]) ; 400 response
  (get-puzzle [:fault]) ; 500 response
  (unknown-request) ; 404 response
  ;
)