(ns trident.datomic-cloud.txauth
  "Functions for authorizing arbitrary transactions"
  (:require [clojure.spec.alpha :as s]
            [datomic.client.api :as d]
            [orchestra.core :refer [defn-spec]]
            [taoensso.timbre :refer [debug]]
            [trident.util :as u]
            [trident.util.datomic :as ud]))

(defn ^:no-doc exists?
  ([db eid]
   (not-empty (d/q '[:find ?e :in $ ?e :where [?e]] db eid)))
  ([db attr value]
   (not-empty (d/q '[:find ?e :in $ ?a ?v :where [?e ?a ?v]] db attr value))))

(defn-spec ent-valid? boolean?
  "Returns true if `ent` matches `spec`.
  Entities referenced by `ent` must also match their respective keys' specs."
  [db any? spec any? ent map?]
  (and (s/valid? spec ent)
       (u/for-every? [[k vs] ent
                      v (ud/wrap-coll vs)
                      :when (and (map? v)
                                 (contains? (s/registry) k))]
         (let [ent (if (empty? (dissoc v :db/id :db/ident))
                     (d/pull db '[*] (:db/id v))
                     v)]
           (s/valid? k ent)))))

(defn authorize
  "Authorizes a transaction `tx` ran by the user `uid`.

  Returns transaction data if successful, throws an exception otherwise.
  `authorizers` is the fully-qualified symbol of a var containing your
  authorization model, e.g. `'your.namespace/authorizers`:
  ```
  (ns your.namespace)

  (clojure.spec.alpha/def ::message
    (trident.util.datomic/ent-keys [:message/text :message/sender]))

  (def authorizers
    {[nil ::message]
     (fn [{:keys [uid eid datoms db-before db-after before after]}]
       (not-empty
         (datomic.client.api/q
           '[:find ?e :in $ ?e ?user :where [?e :message/sender ?user]]
           db-after eid [:user/uid uid])))})
  ```
  This value of `authorizers` will allow a user to create a message entity as
  long as they are listed as the sender of that message.

  `authorizers` is a map from entity \"signatures\" to authorizer functions. The
  authorizer function takes information about a single entity that was changed
  in the transaction, returning true if the change should be allowed. A
  transaction will be authorized only if each entity changed by the transaction
  is authorized by at least one authorizor function.

  The entity must also match the authorizor function's signature. A signature is
  a pair of specs that are matched by a particular entity before and after the
  transaction, respectively. `nil` means that the entity does not exist. So in
  the example, `[nil ::message]` means that the entity is being created (i.e.
  it didn't exist before the transaction) and that it has exactly two keys:
  `:message/text` and `:message/sender` (see [[trident.util.datomic/ent-keys]]).

  The authorizer function takes the following parameters:

   - `uid`: same as the `uid` passed to `authorize`. It should be the ID of the
     user making the transaction, or `nil` if the user is unauthenticated.

   - `eid`: the ID of the entity being authorized.

   - `datoms`: the datoms in `:tx-data` from the transaction result that belong
     to this entity.

   - `db-before` and `db-after`: same as in the transaction result.

   - `before` and `after`: the results of `pull`ing the entity before and after
     the transaction, respectively; or `nil` if the entity doesn't exist."
  [db authorizers uid tx]
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
                    (u/load-var authorizers))

            authorized?
            (u/for-some? [[_ authorize-fn] matching-authorizers]
              (authorize-fn auth-arg))]

        (when (not authorized?)
          (throw (ex-info "Entity change not authorized"
                          {:auth-arg auth-arg
                           :matches matching-authorizers})))))
    tx))

(defn handler
  "A ring handler for authorizing and running transactions.

  To use `handler`, you must include `:allow [trident.datomic-cloud.txauth/authorize
  ...]` in your `ion-config.edn` file.

  Parameters:
  - `conn`: a Datomic connection
  - `tx`: a Datomic transaction
  - `uid` and `authorizers`: see [[authorize]].
  - `allowed`: set of fully-qualified symbols, denoting transaction functions
    that the user is allowed to include in their transaction.

  If authorized, returns a ring response with the value of `:tempids` from the
  transaction result in the body (as EDN). Returns a 403 response otherwise.

  Datomic EIDs in the response are wrapped in a tagged literal, e.g. `123`
  becomes `#trident/eid \"123\"`. See [[trident.datascript/transact!]].
  `handler` assumes that any EIDs in `tx` are normal `long`s, not tagged
  literals. To this end, you may want to include `{trident/eid
  trident.util/parse-int}` in your `data_readers.clj` file."
  [{:keys [allowed conn authorizers uid] {tx :tx} :params :or {allowed #{}} :as req}]
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
                  (map (fn [[k v]] [k (tagged-literal 'trident/eid (str v))]))
                  (into {})
                  pr-str)}
      (catch Exception e
        (do
          (debug {:msg "tx rejected"
                  :ex e
                  :uid uid
                  :tx tx})
          {:status 403
           :body "tx rejected"})))))
