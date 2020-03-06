(ns trident.firestore.util
  (:require-macros
    [cljs.core.async.macros :refer [go]])
  (:require
    [clojure.set :as set]
    [clojure.spec.alpha :as s]
    [clojure.string :as str]
    [clojure.walk :as walk]
    [oops.core :refer [oget ocall oapply+]]
    [trident.util :as u]))

; Will this fail in the browser if Firebase is included as an NPM dependency?
(def Timestamp
  (or
    (u/catchall-js js/firebase.firestore.Timestamp)
    (oget (js/require "firebase-admin") "firestore.Timestamp")))

(defn path->ident [path]
  (let [table-pos (- (count path) 2)
        table (nth path table-pos)
        id (u/pred-> (u/dissoc-vec path table-pos)
             #(= 1 (count %)) first)]
    [table id]))

(defn keywordize-path [path]
  (vec (map-indexed (fn [i x] (cond-> x (even? i) keyword)) path)))

(defn path-str->ident [path-str]
  (-> path-str
    (str/split "/")
    keywordize-path
    path->ident))

(defn ident->path [[table id]]
  (let [id (u/pred-> id string? vector)
        doc-id (when (odd? (count id))
                 (last id))
        parent-collection (cond-> id
                            doc-id butlast)]
    (concat parent-collection
      [table]
      (when doc-id
        [doc-id]))))

(defn path->ref [firestore path]
  (->> path
    (map-indexed vector)
    (reduce (fn [obj [i id]]
              (if (even? i)
                (ocall obj :collection (name id))
                (ocall obj :doc id)))
      firestore)))

(defn ->query [ident-or-query]
  (-> (cond->> ident-or-query
        (not (map? ident-or-query)) (hash-map :ident))
    (update :ident ident->path)
    (set/rename-keys {:ident :path})))

(defn ident->ref [firestore ident]
  (path->ref firestore (ident->path ident)))

(defn query->ref [firestore q]
  (let [{:keys [path collection-group where order-by limit limit-to-last
                start-at start-after end-at end-after]} (->query q)
        r (if collection-group
            (ocall firestore :collectionGroup (name collection-group))
            (path->ref firestore path))
        r (reduce (fn [r [attr op value]]
                    (ocall r :where (name attr) (name op) (clj->js value)))
            r
            where)
        r (reduce (fn [r [arg method]]
                    (if arg
                      (oapply+ r method (u/wrap-vec arg))
                      r))
            r
            [[(some->> order-by u/wrap-vec (map name)) :orderBy]
             [start-at :startAt]
             [start-after :startAfter]
             [end-at :endAt]
             [end-after :endAfter]
             [limit :limit]
             [limit-to-last :limitToLast]])]
    r))

(s/def ::doc-ident
  (s/tuple keyword?
    (s/or
      :doc string?
      :subcoll (s/cat
                 :coll (s/+ (s/cat :table keyword? :id string?))
                 :doc string?))))

(defn coerce-from-fb [doc]
  (walk/postwalk
    (fn [x]
      (if-some [path-str (u/catchall-js (oget x :path))]
        (path-str->ident path-str)
        (cond-> x
          (= (type x) Timestamp) (ocall :toDate))))
    doc))

(defn coerce-coll-idents [firestore coll]
  ((if (map? coll)
     u/map-vals
     mapv)
   #(cond->> %
      (s/valid? ::doc-ident %) (ident->ref firestore))
   coll))

(defn coerce-to-fb [firestore x]
  (walk/postwalk
    (fn [x]
      ; We can't just check (s/valid? ::doc-ident x) here because it matches
      ; key-value pairs in maps.
      (cond
        (inst? x) (ocall Timestamp :fromDate x)
        (coll? x) (coerce-coll-idents firestore x)
        :default  x))
    x))

(defn doc->clj [^js doc]
  (let [ident (-> doc
                (oget "ref.path")
                path-str->ident)]
    (-> (ocall doc :data)
      (js->clj :keywordize-keys true)
      coerce-from-fb
      (assoc :ident ident))))

(defn doc->changeitem [{:keys [doc exists]}]
  (let [{:keys [ident] :as ent} (doc->clj doc)]
    [ident (when exists ent)]))

(defn query-snapshot->changeset [snapshot]
  (for [change (ocall snapshot :docChanges)
        :let [exists (not= "removed" (oget change :type))]]
    (doc->changeitem {:doc (oget change :doc)
                      :exists exists})))

(defn doc->changeset [doc]
  [(doc->changeitem {:doc doc
                     :exists (oget doc :exists)})])

(defn doc-query? [q]
  (let [{:keys [collection-group path]} (->query q)]
    (and (nil? collection-group)
      (even? (count path)))))

(defn write-unsafe
  "See https://clojure.atlassian.net/browse/ASYNC-192"
  [firestore changeset]
  (let [promises
        (for [[ident ent] changeset
              :let [have-doc-id (doc-query? ident)
                    r (ident->ref firestore ident)
                    js-ent (clj->js (coerce-to-fb firestore ent))
                    merge-ent (:merge (meta ent))
                    update-ent (:update (meta ent))]]
          (cond
            (nil? ent)        (ocall r :delete)
            (not have-doc-id) (ocall r :add js-ent)
            merge-ent         (ocall r :set js-ent #js {:merge true})
            update-ent        (ocall r :update js-ent)
            :default          (ocall r :set js-ent)))]
    (doall promises)
    (go
      (doseq [p promises]
        (u/js<! p)))))
