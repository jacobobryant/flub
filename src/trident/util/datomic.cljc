(ns trident.util.datomic
  "Utility functions for working with Datomic.

  Only includes functions that don't depend on Datomic APIs."
  (:require [clojure.string :as str]
            [clojure.spec.alpha :as s]
            [trident.util :as u]
            [orchestra.core :refer [defn-spec]]
            [clojure.set :refer [difference intersection]]))

(defn expand-flags
  "Generate Datomic schema from a collection of flags.

  Example:
  ```
  (expand-flags [:foo :db.type/string :db.cardinality/one
                 \"this is a foo\" [:db/id \"some-id\"]])
  => #:db{:ident :foo,
          :valueType :db.type/string,
          :cardinality :db.cardinality/one,
          :doc \"this is a foo\",
          :id \"some-id\"}
  ```"
  [flags]
  (->> flags
       (map (fn [x]
              (cond
                (vector? x) x
                (string? x) [:db/doc x]
                (= x :db/isComponent) [:db/isComponent true]
                (= x :db/noHistory) [:db/noHistory true]
                :default (condp #(str/starts-with? %2 %1) (str x)
                           ":db.type/"        [:db/valueType x]
                           ":db.cardinality/" [:db/cardinality x]
                           ":db.unique/"      [:db/unique x]
                           [:db/ident x]))))
       (into {})))

(defn datomic-schema
  "Generate Datomic schema from a compact schema format.

  `compact-schema` is a map from idents to flags. See [[expand-flags]]. Example:
  ```
  (datomic-schema {:foo [:db.type/string :db.unique/identity]
                   :bar [:db.type/long :db.cardinality/many]})
  => (#:db{:ident :foo,
           :cardinality :db.cardinality/one,
           :valueType :db.type/string
           :unique :db.unique/identity}
      #:db{:ident :bar,
           :cardinality :db.cardinality/many,
           :valueType :db.type/long})
  ```"
  [compact-schema]
  (map (fn [[ident flags]]
         (let [m (merge {:db/ident ident} (expand-flags flags))]
           (if (and (contains? m :db/valueType) (not (contains? m :db/cardinality)))
             (assoc m :db/cardinality :db.cardinality/one)
             m)))
       compact-schema))

(defn datascript-schema
  "Generate Datascript schema from a compact schema format.
  See [[datomic-schema]]."
  [compact-schema]
  (->> compact-schema
       (map (fn [[ident flags]]
              [ident (u/condas-> (expand-flags flags) x
                       (not= (:db/valueType x) :db.type/ref) (dissoc x :db/valueType))]))
       (remove (comp empty? second))
       (into {})))

(defn composite-keys [m]
  ; assert no subset keys
  (u/map-kv (fn [k v] [k [:db.type/tuple :db.unique/identity [:db/tupleAttrs v]]]) m))

(defn specs [m]
  (u/map-kv (fn [k v] [k [[:db.entity/attrs v]]]) m))

(defmacro defspecs [sym m]
  `(do
     ~@(for [[k v] m]
         `(s/def ~k (s/keys :req ~v)))
     (def ~sym (specs ~m))))

(defn ephemeral [m]
  (u/map-kv (fn [k v] [k (conj v :db/noHistory)]) m))

(defn merge-schema [& ms]
  (when-not (->> ms
                 (map (comp set keys))
                 (apply intersection)
                 empty?)
    (throw (ex-info "schemas have shared keys" {})))
  (apply merge ms))

(defn tag-eids
  "Convert all EIDs in `datoms` to tagged literals.

  `ds-schema`: Datascript schema.

  For example, `123` would become `#trident/eid \"123\"`."
  [ds-schema datoms]
  (for [[e a v] datoms]
    [(tagged-literal 'trident/eid (str e))
     a
     (if (= :db.type/ref (get-in ds-schema [a :db/valueType]))
       (tagged-literal 'trident/eid (str v))
       v)]))

(declare translate-identifier)
(declare translate-n-identifier)
(declare translate-map-form)

(defn ^:no-doc translate-value [ds-schema eids k v]
  (if (= :db.type/ref (get-in ds-schema [k :db/valueType]))
    (translate-n-identifier ds-schema eids v)
    v))

(defn ^:no-doc translate-lookup-ref [ds-schema eids x]
  (let [[identifier value] x
        identifier (translate-identifier ds-schema eids identifier)
        value (translate-value ds-schema eids identifier value)]
    [identifier value]))

(defn ^:no-doc translate-identifier [ds-schema eids x]
  (cond
    (coll? x) (translate-lookup-ref ds-schema eids x)
    (number? x) (eids x)
    :default x))

(defn ^:no-doc translate-n-identifier [ds-schema eids entid]
  (if (string? entid)
    entid
    (translate-identifier ds-schema eids entid)))

(defn ^:no-doc translate-map-value [ds-schema eids k v]
  (if (map? v)
    (translate-map-form ds-schema eids v)
    (translate-value ds-schema eids k v)))

(defn ^:no-doc translate-map-form [ds-schema eids form]
  (->> form
       (map (fn [[k v]]
              [k (if (= :db.cardinality/many (get-in ds-schema [k :db/cardinality]))
                   (map #(translate-map-value ds-schema eids k %) v)
                   (translate-map-value ds-schema eids k v))]))
       (into {})))

(defn translate-eids [ds-schema eids tx]
  "Replaces Datascript entity IDs in a transaction with Datomic entity IDs.

  `ds-schema`: a Datascript schema, e.g. `{:foo/bar {:db/valueType
  :db.type/string, :db/cardinality :db.cardinality/one}}`

  `eids`: a map from Datascript EIDs to Datomic EIDs.

  `tx`: a transaction."
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

(defn wrap-coll
  "Ensure that `ent-value` is wrapped in a vector or set.

  Needed because Datomic `pull` wraps values in collections only sometimes,
  depending on attribute cardinality."
  [ent-value]
  (u/pred-> ent-value (complement (some-fn vector? set?)) vector))

(defn-spec ^:no-doc ent-keys* boolean?
  [req set? all set? ent map?]
  (let [ks (-> ent keys set (disj :db/id))
        registry (s/registry)]
    (and (empty? (difference req ks))
         (empty? (difference ks all))
         (every? #(or (not (contains? registry %))
                      (every? (partial kv-valid? %) (wrap-coll (get ent %))))
                 ks))))

(defn ent-keys
  "Like `clojure.spec.alpha/keys`, but only allow keys in `req` or `opt`.

  Intended for use with a `pull`ed entity. Handles cardinality many values
  correctly. Only checks the current entity; the values of any `:db.type/ref`
  attributes will be assumed valid."
  ([req opt]
   (let [req (set req)
         all (into req opt)]
     (partial ent-keys* req all)))
   ([req]
    (ent-keys req nil)))

#?(:clj

(defn eval-tx-fns
  "Evaluate any transaction functions in `tx`, returning transaction data.

  Only works with classpath transaction functions, not database functions."
  [db tx]
  (apply concat
         (for [[op & args :as form] tx]
           (if (symbol? op)
             (eval-tx-fns db (apply (u/loadf op) db args))
             [form]))))

)
