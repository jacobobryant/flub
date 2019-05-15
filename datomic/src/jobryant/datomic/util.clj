(ns jobryant.datomic.util
  (:require [clojure.string :as str]
            [clojure.pprint :refer [pprint]]
            [clojure.spec.alpha :as s]
            [jobryant.util :as u]
            [datomic.api :as d]))

(defn exists?
  ([db eid]
   (boolean (d/q '[:find ?e . :in $ ?e :where [?e]] db eid)))
  ([db attr value]
   (boolean (d/q '[:find ?e . :in $ ?a ?v :where [?e ?a ?v]] db attr value))))

(defn ref? [db attr]
  (= :db.type/ref (:value-type (d/attribute db attr))))

(defn tx-fn [fn-sym]
  (let [fn-var (resolve fn-sym)
        params (-> fn-var meta :arglists first)]
    {:db/ident (keyword fn-sym)
     :db/fn (d/function
              {:lang "clojure"
               :params params
               :requires [[(-> fn-sym namespace symbol)]]
               :code `(~fn-sym ~@params)})}))

(defn ns-tx-fns [ns-sym]
  (map #(tx-fn (symbol (str ns-sym) (str (first %))))
       (ns-publics ns-sym)))
