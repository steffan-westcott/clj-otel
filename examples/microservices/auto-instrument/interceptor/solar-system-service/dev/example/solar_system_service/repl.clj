(ns example.solar-system-service.repl
  "Functions to operate and exercise the running service at the REPL."
  (:require [clojure.data.json :as json]
            [example.common.system.main :as common-main]
            [example.solar-system-service.env :as env]
            [example.solar-system-service.main :as main]
            [example.solar-system-service.system :as system]
            [nrepl.server :as nrepl]
            [org.httpkit.client :as client]))


(defn- run-nrepl
  []
  (let [server (nrepl/start-server (:nrepl (env/read-config)))]
    (common-main/add-shutdown-hook (fn stop-nrepl []
                                     (nrepl/stop-server server)))))


(defn -main
  "Starts the system and an embedded nREPL server for remote REPL access.
   They are stopped when a terminate signal is received by the JVM.
   Intended for use by `clojure` command."
  [& opts]
  (run-nrepl)
  (main/-main opts))


(defn start!
  "Ensures the system is started. Intended for use in local or remote REPL."
  []
  (system/start!))


(defn stop!
  "Ensure the system is stopped. Intended for use in local or remote REPL."
  []
  (system/stop!))


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


(defn get-statistics
  "Request the running system for statistics on the given planet."
  [planet]
  (do-get-request "/statistics"
                  {:query-params {"planet" (name planet)}
                   :headers      {"Accept" "application/json"}}))


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
  (stop!)
  (start!)
  ;
)

