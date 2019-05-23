(ns trident.datomic-cloud.txauth
  (:require [clojure.spec.alpha :as s]
            [datomic.client.api :as d]
            [orchestra.core :refer [defn-spec]]
            [taoensso.timbre :refer [debug]]
            [trident.util :as u]))

(defn exists?
  ([db eid]
   (not-empty (d/q '[:find ?e :in $ ?e :where [?e]] db eid)))
  ([db attr value]
   (not-empty (d/q '[:find ?e :in $ ?a ?v :where [?e ?a ?v]] db attr value))))

(defn-spec ent-valid? boolean?
  [db any? spec any? ent map?]
  (and (s/valid? spec ent)
       (u/for-every? [[k vs] ent
                      v (u/wrap-vec vs)
                      :when (and (map? v)
                                 (contains? (s/registry) k))]
         (let [ent (if (empty? (dissoc v :db/id :db/ident))
                     (d/pull db '[*] (:db/id v))
                     v)]
           (s/valid? k ent)))))

(defn authorize [db authorizers uid tx]
  (let [{:keys [tx-data db-before db-after] :as result} (d/with db {:tx-data tx})]
    (doseq [[e datoms] (group-by :e (rest tx-data))]
      (let [[before after :as ents]
            (map #(when (exists? % e) (d/pull % '[*] e)) [db-before db-after])

            auth-arg {:uid uid
                      :db-before db-before
                      :db-after db-after
                      :datoms datoms
                      :before before
                      :after after
                      :eid e}

            matching-authorizers
            (filter (fn [[specs _]]
                      (u/for-every? [[spec ent db]
                                     (map vector specs ents [db-before db-after])]
                          (and (= (some? spec) (some? ent))
                               (or (nil? spec) (ent-valid? db spec ent)))))
                    (u/loadx authorizers))

            authorized?
            (u/for-some? [[_ authorize-fn] matching-authorizers]
              (authorize-fn auth-arg))]

        (when (not authorized?)
          (throw (ex-info "Entity change not authorized"
                          {:auth-arg auth-arg
                           :matches matching-authorizers})))))
    tx))

(defn handler [{:keys [allowed conn authorizers params uid]
                :or {allowed #{}} :as req}]
  (let [tx (:tx params)]
    (if-some [bad-fn (some #(and (symbol? %) (not (contains? allowed %)))
                           (map first tx))]
      (do
        (debug {:msg "tx not allowed"
                :bad-fn bad-fn
                :uid uid
                :tx tx})
        {:status 403
         :body (str "tx fn not allowed: " bad-fn)})
      (try
        {:headers {"Content-type" "application/edn"}
         :body (->> (d/transact conn {:tx-data [[`authorize authorizers uid tx]]})
                    :tempids
                    (map (fn [[k v]] [k (tagged-literal 'eid (str v))]))
                    (into {})
                    pr-str)}
        (catch Exception e
          (do
            (debug {:msg "tx rejected"
                    :ex e
                    :uid uid
                    :tx tx})
            {:status 403
             :body "tx 0ejected"}))))))
