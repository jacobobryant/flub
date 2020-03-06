(ns trident.firestore
  (:require-macros
    [cljs.core.async.macros :refer [go]]
    [trident.firestore :refer [write]])
  (:require
    [trident.firestore.util :as util]
    [cljs.core.async :refer [chan put!]]
    [oops.core :refer [oget ocall]]
    [trident.util :as u]))

(defn subscribe [firestore queries]
  (let [c (chan)
        *unsubscribe (atom nil)
        put-changeset! (fn [changeset]
                         (when-not (put! c changeset)
                           (some-> @*unsubscribe #(%))))
        fns (for [q queries]
              (ocall (util/query->ref firestore q) :onSnapshot
                (comp put-changeset!
                  (if (util/doc-query? q)
                    util/doc->changeset
                    util/query-snapshot->changeset))))
        unsubscribe (fn [] (mapv #(%) fns))]
    (reset! *unsubscribe unsubscribe)
    (doall fns)
    c))

(defn query [firestore q]
  (assert (not (util/doc-query? q))
    "You cannot query for a specific document; use pull instead.")
  (let [result (ocall (util/query->ref firestore q) :get)]
    (go (map util/doc->clj (oget (u/js<! result) :docs)))))

(defn pull [firestore ident]
  (assert (and (not (map? ident)) (util/doc-query? ident))
    "You cannot pull multiple documents; use query instead.")
  (let [result (ocall (util/ident->ref firestore ident) :get)]
    (go
      (let [doc (u/js<! result)]
        (when (oget doc :exists)
          (util/doc->clj doc))))))

(defn doc-exists? [firestore ident]
  (let [result (ocall (util/ident->ref firestore ident) :get)]
    (go (oget (u/js<! result) :exists))))

(defn merge-changeset [db changeset]
  (reduce (fn [db [[table id] ent]]
            (if ent
              (assoc-in db [table id] ent)
              (update db table dissoc id)))
    db
    changeset))

(defn doc->key [{[table id] :ident}]
  (u/pred-> id coll? last))
