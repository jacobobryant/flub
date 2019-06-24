(ns trident.web
  "Highly contrived web framework.

  Great for making websites that look exactly like the one I made with this."
  (:require [clojure.spec.alpha :as s]
            [datomic.client.api :as d]
            [orchestra.core :refer [defn-spec]]
            [reitit.ring :as reitit]
            [ring.middleware.cors :as cors]
            [ring.middleware.defaults :as defaults]
            [ring.middleware.format-params :as fmt-params]
            [trident.datomic-cloud :as tcloud]
            [trident.datomic-cloud.txauth :as txauth]
            [trident.firebase :as firebase]
            [trident.ion :as tion]
            [trident.ring :as tring]
            [trident.util.datomic :as ud]))

(s/def ::config (s/keys :req-un [::env ::app-name ::schema ::db-name
                                 ::origins ::datoms-for ::authorizers]
                        :opt-un [::local-tx-fns?]))

(defn verify-token [param-client token]
  (->> :firebase-key
       (tion/get-param param-client)
       (firebase/verify-token token)))

(defn init-handler [{:keys [conn datoms-for ds-schema claims uid] :as req}]
  (let [tx [{:user/uid uid
             :user/email (claims "email")
             :user/emailVerified (claims "email_verified")}]
        {:keys [db-after] :as result} (d/transact conn {:tx-data tx})
        datoms (->> (datoms-for db-after uid)
                    (ud/tag-eids ds-schema)
                    pr-str)]
    {:headers {"Content-Type" "application/edn"}
     :body datoms}))

(defn-spec start any? [{:keys [origins schema datoms-for] :as config} ::config]
  (let [param-client (tion/map->ParamClient (select-keys config [:app-name :env]))
        conn (tcloud/init-conn (assoc config :client-cfg (tion/default-config)))
        ds-schema (ud/datascript-schema schema)]
    (reitit/ring-handler
      (reitit/router
        [["/init" {:get init-handler
                   :name ::init}]
         ["/tx" {:post txauth/handler
                 :name ::tx}]]
        {:data {:middleware [tring/wrap-catchall
                             [defaults/wrap-defaults defaults/api-defaults]
                             fmt-params/wrap-clojure-params
                             [cors/wrap-cors
                              :access-control-allow-origin origins
                              :access-control-allow-methods [:get :post]
                              :access-control-allow-headers ["Authorization" "Content-Type"]]
                             [tring/wrap-uid
                              {:verify-token (partial verify-token param-client)}]
                             [tring/wrap-request
                              #(merge % (select-keys config [:datoms-for :authorizers])
                                      {:conn conn
                                       :ds-schema ds-schema})]]}}))))

(defn init! [config]
  (tion/set-timbre-ion-appender!)
  (start config))
