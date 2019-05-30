(ns trident.datomic-cloud.client
  (:require [trident.util :as u]
            [trident.util.datomic :as ud]
            [datomic.client.api :as d]
            [datomic.client.api.protocols :as client-proto]
            [datomic.client.api.impl :as client-impl]))

(u/inherit LocalTxDb [db]
  client-proto/Db
  (with [_ arg-map]
    (->> #(ud/eval-tx-fns db %)
         (update arg-map :tx-data)
         (d/with db))))

(u/inherit LocalTxConnection [conn]
  client-proto/Connection
  (with-db [_] (LocalTxDb. (d/with-db conn)))
  (transact [this arg-map]
    ; TODO make this guaranteed to use the latest db
    (locking this
      (->> #(ud/eval-tx-fns (LocalTxDb. (d/with-db conn)) %)
           (update arg-map :tx-data)
           (d/transact conn))))
  clojure.lang.ILookup)

(defn connect [client {:keys [local-tx-fns?] :as config}]
  (cond-> (d/connect client (dissoc config :local-tx-fns?))
    local-tx-fns? (LocalTxConnection.)))
