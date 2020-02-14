(ns trident.datomic-cloud
  (:require
    [clojure.walk :as walk]
    [datomic.client.api :as d]
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
       (doseq [schema (u/split-by #(empty? (select-keys % [:db/tupleAttrs :db.entity/attrs]))
                                  (ud/datomic-schema (:schema config)))
               :when (not-empty schema)]
         (d/transact conn {:tx-data schema}))
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

(defn pull-in
  ([db [lookup & path]]
   (let [[fst & rst] (reverse path)
         pattern (reduce (fn [pattern k]
                           [{k pattern}])
                   [fst]
                   rst)]
     (get-in (d/pull db pattern lookup) path)))
  ; deprecated
  ([db path lookup]
   (pull-in db (apply vector lookup path))))

(defn many-op [op id k v many?]
  (mapv (fn [v] [op id k v]) (cond-> v (not many?) vector)))

; todo optimize with delayed pulls
(defn tx* [db new-ent old-ent]
  (u/forcat [[k new-val] new-ent
             :let [old-val (get old-ent k)]
             :when (and (not= :db/id k)
                     (not= new-val old-val))
             :let [attr-info (d/pull db [:db/valueType :db/isComponent :db/cardinality] k)
                   ref? (= :db.type/ref (-> attr-info :db/valueType :db/ident))
                   component? (:db/isComponent attr-info)
                   many? (= :db.cardinality/many (-> attr-info :db/cardinality :db/ident))
                   resolved-old-val (u/pred-> old-val map? :db/id)
                   resolved-new-val (if (map? new-val)
                                      (get old-val :db/id
                                        (when component?
                                          (str (java.util.UUID/randomUUID))))
                                      new-val)
                   many-missing (when many?
                                  (->> resolved-old-val
                                    (remove (set resolved-new-val))
                                    (map #(u/pred-> % map? :db/id))))]]
    (if (some? new-val)
      (cond->
        (many-op :db/add (:db/id new-ent) k resolved-new-val many?)
        many? (into (many-op :db/retract (:db/id new-ent) k many-missing true))
        component? (into (tx* db (assoc new-val :db/id resolved-new-val) old-val)))
      (if component?
        [[:db/retractEntity resolved-old-val]]
        (many-op :db/retract (:db/id old-ent) k resolved-old-val many?)))))

(defn tx
  "Convert m to a Datomic transaction"
  [db m lookup]
  (if (some? m)
    (let [old-ent (d/pull db (conj (keys m) :db/id) lookup)
          new-id (or (:db/id old-ent) (str (java.util.UUID/randomUUID)))]
      (tx* db (assoc m :db/id new-id) old-ent))
    [[:db/retractEntity lookup]]))

; deprecated
(defn pull-attr [db attr lookup]
  (pull-in db [attr] lookup))

(defn pull-id [db lookup]
  (pull-in db [lookup :db/id]))

(defn upsert-component [db lookup attr m]
  {:db/id lookup
   attr (u/assoc-some m
          :db/id (pull-in db [lookup attr :db/id]))})

(defn q-with [q replacements & args]
  (apply d/q
    (walk/postwalk #(cond-> %
                      (contains? replacements %) replacements)
      q)
    args))
