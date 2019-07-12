(ns trident.datomic-cloud
  (:require [datomic.client.api :as d]
            [trident.util :as u]
            [trident.util.datomic :as ud]
            [trident.datomic-cloud.client :as client]))

(defn init-conn
  "Initializes and returns Datomic cloud connection.

  `config` is a map with the following keys:
   - `:db-name`: the name of a database that will be created
   - `:schema`: the datomic schema in compact format. See
     [[trident.util.datomic/datomic-schema]].
   - `:local-tx-fns?`: if true, transaction functions will be evaluated locally.
     See [[trident.util.datomic/eval-tx-fns]]
   - `:client-cfg`: if `client` is omitted, this will be used to create one."
  ([client config]
   (do
     (d/create-database client (select-keys config [:db-name]))
     (let [conn (client/connect client (select-keys config [:db-name :local-tx-fns?]))]
       (d/transact conn {:tx-data (ud/datomic-schema (:schema config))})
       conn)))
  ([config]
   (init-conn (d/client (:client-cfg config)) config)))

(defn pull-many
  "Ordering is not preserved. If `map-from-key` is provided, returns a map. See
  [[trident.util/map-from]]. nil keys are `dissoc`-ed."
  ([db pull-expr eids]
   (flatten
     (d/q [:find (list 'pull '?e pull-expr)
           :in '$ '[?e ...]]
          db eids)))
  ([db pull-expr map-from-key eids]
   (let [result (u/map-from map-from-key (pull-many db pull-expr eids))]
     (dissoc result nil))))
