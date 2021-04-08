(ns flub.crux
  (:require
    [flub.core :as flub]
    [flub.malli :as fmalli]
    [clojure.java.io :as io]
    [crux.api :as crux]))

(defn start-node* [{:flub.crux/keys [topology dir opts] :as sys}]
  (let [rocksdb (fn [basename]
                  {:kv-store {:crux/module 'crux.rocksdb/->kv-store
                              :db-dir (io/file dir basename)}})
        node (crux/start-node
               (merge
                 (case topology
                   :standalone
                   {:crux/index-store    (rocksdb "index")
                    :crux/document-store (rocksdb "docs")
                    :crux/tx-log         (rocksdb "tx-log")}

                   :jdbc
                   {:crux/index-store (rocksdb "index")
                    :crux.jdbc/connection-pool
                    {:dialect {:crux/module 'crux.jdbc.psql/->dialect}
                     :pool-opts (flub/select-ns-as sys 'flub.crux.jdbc-pool nil)
                     :db-spec (flub/select-ns-as sys 'flub.crux.jdbc nil)}
                    :crux/tx-log {:crux/module 'crux.jdbc/->tx-log
                                  :connection-pool :crux.jdbc/connection-pool}
                    :crux/document-store {:crux/module 'crux.jdbc/->document-store
                                          :connection-pool :crux.jdbc/connection-pool}})
                 opts))]
    (crux/sync node)
    node))

(defn start-node [sys]
  (let [node (start-node* sys)]
    (-> sys
      (assoc :flub.crux/node node)
      (update :flub/stop conj #(.close node)))))

(defn wrap-db [handler {:keys [node use-open-db]
                        :or {use-open-db false}}]
  (fn [req]
    (let [db (delay ((if use-open-db crux/open-db crux/db) node))
          resp (handler (assoc req :flub.crux/db db))]
      (when (and use-open-db (realized? db))
        (.close @db))
      resp)))

(defn q [db {:keys [q-fn find args]
             :or {q-fn crux/q} :as query}]
  (let [has-args (contains? query :args)
        query (cond-> query
                has-args (update :args vec)
                true (update :where vec)
                true (dissoc :q-fn))]
    (when (or (not has-args) (not-empty args))
      (if (symbol? find)
        (map first (q-fn db (update query :find vector)))
        (q-fn db query)))))

(defn lazy-q [db {:keys [find] :as query} f]
  (with-open [results (q db
                        (cond-> query
                          (symbol? find) (update :find vector)
                          true (assoc :q-fn crux/open-q)))]
    (f (cond->> (iterator-seq results)
         (symbol? find) (map first)))))

(defn q-entity [db kvs]
  (ffirst
    (q db
      {:find '[(pull doc [*])]
       :where (for [kv kvs]
                (into ['doc] kv))})))

(defn normalize-tx [malli-opts flub-tx]
  (for [args flub-tx]
    (if (keyword? (first args))
      args
      (let [[[schema id] doc & args] args
            id (or id (java.util.UUID/randomUUID))
            doc (cond-> doc
                  true (assoc :crux.db/id id)
                  (map? id) (merge id))]
        (fmalli/assert schema doc malli-opts)
        (into [:crux.tx/put doc] args)))))

(defn submit-tx [{:keys [flub.crux/node] :as opts} flub-tx]
  (crux/submit-tx node
    (doto (normalize-tx (flub/select-ns-as opts 'flub.malli nil) flub-tx)
      flub/pprint)))

(defn submit-await-tx [{:keys [flub.crux/node] :as opts} flub-tx]
  (crux/await-tx node (submit-tx opts flub-tx)))
