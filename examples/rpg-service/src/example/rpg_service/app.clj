(ns example.rpg-service.app
  "Core application logic. This is a simple application which performs
   read-only queries on an EQL database and updates query count metrics."
  (:require [honeyeql.core :as heql]
            [steffan-westcott.clj-otel.api.metrics.instrument :as instrument]
            [steffan-westcott.clj-otel.api.trace.span :as span]))


(defn- do-query
  "Performs `query` on EQL database `eql-db`."
  [{:keys [eql-db instruments]} subject query]

  ;; Increment `:query-count` counter for queries on `subject`
  (instrument/add! (:query-count instruments)
                   {:value      1
                    :attributes {:subject subject}})

  (heql/query-single eql-db query))



(defn- query-encumbrance
  "Returns the total weight of inventory items carried by RPG character with
   the given `id`."
  [components id]

  ;; Add a span around encumbrance calculation
  (span/with-span! "Calculating encumbrance"

    (let [query {[:character/id id] [{:character/items [[:sum :item/weight]]}]}
          res   (do-query components :encumbrance query)]
      (or (get-in res [:character/items 0 :item/sum-of-weight]) 0))))



(defn query-character
  "Returns details for the RPG character with the given `id`."
  [components id]
  (some-> (do-query components :character {[:character/id id] [:character/*]})
          (assoc :character/encumbrance (query-encumbrance components id))))



(defn query-inventory
  "Returns inventory belonging to RPG character with the given `id`."
  [components id]
  (do-query components
            :inventory
            {[:character/id id] [{[:character/items :as :character/inventory]
                                  [:item/id :item/description]}]}))



(defn query-item
  "Returns details for the item with the given `id`."
  [components id]
  (do-query components :item {[:item/id id] [:item/id :item/description :item/weight]}))
