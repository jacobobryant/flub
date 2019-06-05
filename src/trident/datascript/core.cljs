(ns trident.datascript.core
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

(def ^:private registry (atom []))

(defn register! [f]
  (let [model (with-meta (r/atom (f)) {:f f})]
    (swap! registry conj model)
    model))

(defn invalidate! [& queries]
  (doseq [query (or (not-empty queries) @registry)]
    (let [f (:f (meta query))]
      (reset! query (f)))))

(defn- reverse-tempids [tx-result f]
  (-> (:tempids tx-result)
      (dissoc :db/current-tx)
      (->>
        (map (fn [[k v]] [v (f k)]))
        (into {}))))

; todo:
; - infer new entity ids, replace with strings
; - rollback failed transactions
(defn transact! [persist-fn conn tx & queries]
  (let [tx-result (d/transact! conn tx)]
    (apply invalidate! queries)
    (go (let [tx (ud/translate-eids (:schema @conn) (::eids @conn) tx)
              eids (<! (persist-fn tx))
              tempids (reverse-tempids tx-result eids)]
          (swap! conn update ::eids merge tempids)))
    tx-result))

(defn init-from-datomic! [conn datoms]
  (let [tx (->> datoms
                (postwalk #(u/pred-> % u/instant? from-date))
                (map concat (repeat [:db/add])))
        eids (reverse-tempids (d/transact! conn tx) #(tagged-literal 'eid %))]
    (swap! conn assoc ::eids eids))
  (invalidate!))
