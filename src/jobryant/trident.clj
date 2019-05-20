(ns jobryant.trident
  (:require [jobryant.util :as u]
            [jobryant.datomic-cloud.txauth :as txauth]
            [jobryant.datomic-cloud.client :refer [connect]]
            [jobryant.firebase :refer [verify-token]]
            [jobryant.ion :as jion]
            [datomic.ion :as ion]
            [datomic.client.api :as d]
            [clojure.spec.alpha :as s]
            [mount.core :refer [defstate start]]
            [orchestra.core :refer [defn-spec]]
            [compojure.core :refer [defroutes GET POST]]
            [datomic.ion.lambda.api-gateway :refer [ionize]]))

(s/def ::config (fn [x] (every? #(contains? x %) [:env :app-name])))

(u/defconfig
  {:uid-opts {:verify-token (fn [token] (verify-token token #(jion/get-param :firebase-key)))}
   :env :dev
   :db-name "dev"
   :client-cfg {:system ^:derived #(:app-name %)
                :endpoint ^:derived #(str "http://entry." (:app-name %)
                                          ".us-east-1.datomic.net:8182/")
                :server-type :ion
                :region "us-east-1"
                :proxy-port 8182}
   :local-tx-fns? false
   :local-routing? false})

(defstate client :start (d/client (:client-cfg config)))
(defstate conn :start
  (do
    (d/create-database client (select-keys config [:db-name]))
    (let [conn (connect client (select-keys config [:db-name :local-tx-fns?]))]
      (d/transact conn {:tx-data (u/datomic-schema (:schema config))})
      conn)))

(defn init-handler [{:keys [claims uid] :as req}]
  (let [tx [{:user/uid uid
             :user/email (claims "email")
             :user/emailVerified (claims "email_verified")}]
        {:keys [db-after] :as result} (d/transact conn {:tx-data tx})
        datoms (pr-str ((:datoms-for config) db-after uid))]
    {:headers {"Content-Type" "application/edn"}
     :body datoms}))

; replace with reitit
(defn routes [req]
  (case (:uri req)
    "/init" (init-handler req)
    "/tx" (txauth/handler
            (merge req
                   {:conn conn
                    :authorizers (:authorizers config)}))))

(defn-spec init! any? [default-config ::config]
  (jion/set-timbre-ion-appender!)

  (init-config! default-config (ion/get-env))
  (jion/init-config! (select-keys config [:env :app-name]))

  (def handler* (u/wrap-ion-defaults routes (merge (select-keys config [:origins :uid-opts])
                                                   {:conn-var #'conn})))
  (def handler (ionize handler*)))
