(ns trident.datomic-cloud.client
  "An alternate Datomic Cloud client implementation."
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

(u/inherit LocalTxConnection [conn latest-db]
  client-proto/Connection
  (with-db [_] (LocalTxDb. (d/with-db conn)))
  (transact [this arg-map]
    (locking this
      (let [result (->> (LocalTxDb. (or @latest-db (d/with-db conn)))
                        (partial ud/eval-tx-fns)
                        (update arg-map :tx-data)
                        (d/transact conn))]
        (reset! latest-db (:db-after result))
        result)))
  clojure.lang.ILookup)

(defn connect
  "Returns a Datomic Cloud connection, optionally evaluating tx fns locally.

  See [[trident.util.datomic/eval-tx-fns]]. This can be useful for development
  but shouldn't be used in production. If `local-tx-fns?` is true, all
  transactions must go through this connection; otherwise, transaction functions
  might be evaluated with an out-of-date db.

  There is a small edge case where the very first transaction ran through this
  connection will get an old database. This could be eliminated by running an
  empty transaction right after connecting."
  [client {:keys [local-tx-fns?] :as config}]
  (let [conn (d/connect client (dissoc config :local-tx-fns?))]
    (cond-> conn local-tx-fns? (LocalTxConnection. (atom nil)))))
