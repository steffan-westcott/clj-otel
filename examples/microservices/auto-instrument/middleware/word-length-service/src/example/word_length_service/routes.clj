(ns example.word-length-service.routes
  "HTTP routes."
  (:require [example.word-length-service.app :as app]
            [reitit.ring :as ring]
            [ring.util.response :as response]))


(defn- get-ping
  "Returns an empty response."
  [_]
  (response/response nil))



(defn- get-length
  "Returns a response containing the word length."
  [components {{{:keys [word]} :query} :parameters}]

  ;; Simulate a client error for some requests. Exception data is added as attributes to the
  ;; exception event by default.
  (if (= word "problem")
    (throw (ex-info "Bad word argument"
                    {:type          ::ring/response
                     :response      {:status 400
                                     :body   "Bad word argument"}
                     :service/error :service.word-length.errors/bad-word
                     :system/word   word}))
    (response/response (str (app/word-length components word)))))



(defn routes
  "Route data for all routes."
  [components]
  [["/ping" {:get get-ping}]
   ["/length"
    {:get {:handler    (partial get-length components)
           :parameters {:query [:map [:word :string]]}
           :responses  {200 {:body [:string]}}}}]])
