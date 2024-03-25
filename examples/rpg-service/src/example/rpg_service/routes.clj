(ns example.rpg-service.routes
  "HTTP routes."
  (:require [example.rpg-service.app :as app]
            [ring.util.response :as response]))


(defn- response
  "Returns 200 response or 404 if nil."
  [resp]
  (if resp
    (response/response resp)
    (response/not-found nil)))



(defn- handle-get-ping
  "Returns an empty response."
  [_]
  (response/response nil))



(defn- handle-get-character
  "Returns a response containing information on the RPG character with the
   given id."
  [components {{{:keys [id]} :path} :parameters}]
  (response (app/query-character components id)))



(defn- handle-get-inventory
  "Returns a response containing the inventory carried by the RPG character
   with the given id."
  [components {{{:keys [id]} :path} :parameters}]
  (response (app/query-inventory components id)))



(defn- handle-get-item
  "Returns a response containing information on the item with the given id."
  [components {{{:keys [id]} :path} :parameters}]
  (response (app/query-item components id)))



(defn routes
  "Route data for all routes."
  [components]
  [["/ping" {:get handle-get-ping}]
   ["/character/:id"
    {:get {:handler    (partial handle-get-character components)
           :parameters {:path [:map [:id :int]]}
           :responses  {200 {:body [:map ;
                                    [:character/id :int] ;
                                    [:character/name :string] ;
                                    [:character/encumbrance :int]]}}}}]
   ["/character/:id/inventory"
    {:get {:handler    (partial handle-get-inventory components)
           :parameters {:path [:map [:id :int]]}
           :responses  {200 {:body [:map
                                    [:character/inventory
                                     [:vector
                                      [:map            ;
                                       [:item/id :int] ;
                                       [:item/description :string]]]]]}}}}]
   ["/item/:id"
    {:get {:handler    (partial handle-get-item components)
           :parameters {:path [:map [:id :int]]}
           :responses  {200 {:body [:map ;
                                    [:item/id :int] ;
                                    [:item/description :string] ;
                                    [:item/carried-by :int] ;
                                    [:item/weight :int]]}}}}]])
