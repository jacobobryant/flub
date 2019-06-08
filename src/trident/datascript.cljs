(ns trident.datascript
  "Frontend tools for syncing Datascript with Datomic."
  (:require [reagent.core :as r]
            [datascript.core :as d]
            [trident.util :as u]
            [trident.util.datomic :as ud]
            [cljs-time.instant]
            [cljs-time.coerce :refer [from-date]]
            [clojure.walk :refer [postwalk]]
            [cljs.core.async :refer [<!]])
  (:require-macros [trident.util :as u]
                   [cljs.core.async.macros :refer [go]]))

(u/cljs-import-vars datascript.core
                    q pull pull-many create-conn db)

(def ^:no-doc registry (atom []))

(defn ^:no-doc register! [f]
  (let [model (with-meta (r/atom (f)) {:f f})]
    (swap! registry conj model)
    model))

(defn ^:no-doc invalidate! [& queries]
  (doseq [query (or (not-empty queries) @registry)]
    (let [f (:f (meta query))]
      (reset! query (f)))))

(defn eid-mapping
  "Returns a map from Datascript EIDs to Datomic EIDs.

  `datomic-tempids` is a function from tempids in `datascript-tx-result` to
  Datomic EIDs."
  [datascript-tx-result datomic-tempids]
  (let [datascript-tempids (dissoc (:tempids tx-result) :db/current-tx)]
    (u/map-kv #(vector (datascript-tempids %) (datomic-tempids %))
              (keys datascript-tempids))))

; todo:
; - infer new entity ids, replace with strings
; - rollback failed transactions
(defn transact!
  "Transacts `tx` to `conn`, keeping EIDs in sync with Datomic.

  `persist-fn` is a function which takes `tx`, persists to Datomic, and returns
  a channel which will deliver the value of `:tempids` from the Datomic
  transaction result.

  Before passing `tx` to `persist-fn`, Datascript EIDs will be changed to
  Datomic EIDs (stored from previous transactions). Datomic IDs are represented
  as tagged literals, e.g. `#trident/eid \"12345\"`. The backend should encode
  (and decode) EIDs in this form because Datomic entity IDs can be larger than
  Javascript's `Number.MAX_SAFE_INTEGER`.

  Note: currently, if `tx` includes new entities, they must have an explicit
  tempid, e.g. `{:db/id \"tmp\", :foo \"bar\"}` instead of just
  `{:foo \"bar\"}`."
  [persist-fn conn tx & queries]
  (let [tx-result (d/transact! conn tx)]
    (apply invalidate! queries)
    (go (let [tx (ud/translate-eids (:schema @conn) (::eids @conn) tx)
              datomic-tempids (<! (persist-fn tx))
              eids (eid-mapping tx-result datomic-tempids)]
          (swap! conn update ::eids merge eids)))
    tx-result))

(defn init-from-datomic!
  "Loads Datomic datoms into a Datascript `conn`.

  Stores Datomic EIDs for use by [[transact!]]. The Datomic EIDs must be sent
  from the backend as tagged literals; see [[transact!]]."
  [conn datoms]
  (let [tx (->> datoms
                (postwalk #(cond
                             (u/instant? %)                  (from-date %)
                             (and (tagged-literal? %)
                                  (= (:tag %) 'trident/eid)) (:form %)))
                (map concat (repeat [:db/add])))
        tx-result (d/transact! conn tx)
        eids (eid-mapping tx-result #(tagged-literal 'trident/eid %))]
    (swap! conn assoc ::eids eids))
  (invalidate!))
