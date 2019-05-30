(ns trident.util.datomic
  (:require [clojure.string :as str]
            [clojure.spec.alpha :as s]
            [trident.util :as u]
            [orchestra.core :refer [defn-spec]]
            [clojure.set :refer [difference]]))

(defn expand-flags [xs]
  (->> xs
       (map (fn [x]
              (condp #(str/starts-with? %2 %1) (str x)
                ":db.type/"        [:db/valueType x]
                ":db.cardinality/" [:db/cardinality x]
                ":db.unique/"      [:db/unique x]
                ":db/isComponent"  [:db/isComponent true]
                [:db/doc x])))
       (into {})))

(defn datomic-schema [schema]
  (->> schema
       (map (fn [[k v]] [k (expand-flags v)]))
       (map (fn [[k v]]
              (cond
                (empty? v) {:db/ident k}
                :else      (merge {:db/ident k
                                   :db/cardinality :db.cardinality/one}
                                  v))))))

(defn datascript-schema [schema]
  (->> schema
       (map (fn [[k v]] [k (u/condas-> (expand-flags v) x
                             (not= (:db/valueType x) :db.type/ref) (dissoc x :db/valueType))]))
       (remove (comp empty? second))
       (into {})))

(defn stringify-eids [ds-schema datoms]
  (for [[e a v] datoms]
    [(str e) a (if (= :db.type/ref (get-in ds-schema [a :db/valueType]))
                 (str v)
                 v)]))

(declare translate-identifier)
(declare translate-n-identifier)
(declare translate-map-form)

(defn- translate-value [ds-schema eids k v]
  (if (= :db.type/ref (get-in ds-schema [k :db/valueType]))
    (translate-n-identifier ds-schema eids v)
    v))

(defn- translate-lookup-ref [ds-schema eids x]
  (let [[identifier value] x
        identifier (translate-identifier ds-schema eids identifier)
        value (translate-value ds-schema eids identifier value)]
    [identifier value]))

(defn- translate-identifier [ds-schema eids x]
  (cond
    (coll? x) (translate-lookup-ref ds-schema eids x)
    (number? x) (eids x)
    :default x))

(defn- translate-n-identifier [ds-schema eids entid]
  (if (string? entid)
    entid
    (translate-identifier ds-schema eids entid)))

(defn- translate-map-value [ds-schema eids k v]
  (if (map? v)
    (translate-map-form ds-schema eids v)
    (translate-value ds-schema eids k v)))

(defn- translate-map-form [ds-schema eids form]
  (->> form
       (map (fn [[k v]]
              [k (if (= :db.cardinality/many (get-in ds-schema [k :db/cardinality]))
                   (map #(translate-map-value ds-schema eids k %) v)
                   (translate-map-value ds-schema eids k v))]))
       (into {})))

(defn translate-eids [ds-schema eids tx]
  (let [ds-schema (assoc ds-schema :db/id {:db/valueType :db.type/ref})]
    (for [form tx]
      (if (map? form)
        (translate-map-form ds-schema eids form)
        (let [[op entid attr value :as xs] form
              entid (delay (translate-n-identifier ds-schema eids entid))]
          (cond
            (contains? #{:db/add :db/retract} op)
            [op @entid attr (translate-value ds-schema eids attr value)]

            (= :db/retractEntity op) [op @entid]

            :else xs))))))

(defn- kv-valid? [k v]
  (or (map? v) (s/valid? k v)))

(defn wrap-vec
  "Only for use with Datomic ref values"
  [x]
  (u/pred-> x (complement (some-fn vector? set?)) vector))

(defn-spec check-ent-spec boolean?
  [req set? all set? ent map?]
  (let [ks (-> ent keys set (disj :db/id))
        registry (s/registry)]
    (and (empty? (difference req ks))
         (empty? (difference ks all))
         (every? #(or (not (contains? registry %))
                      (every? (partial kv-valid? %) (wrap-vec (get ent %))))
                 ks))))

(defn ent-spec
  ([req opt]
   (let [req (set req)
         all (into req opt)]
     (partial check-ent-spec req all)))
   ([req]
    (ent-spec req nil)))

#?(:clj

(defn eval-tx-fns [db tx]
  (apply concat
         (for [[op & args :as form] tx]
           (if (symbol? op)
             (eval-tx-fns db (apply (u/loadf op) db args))
             [form]))))

)
