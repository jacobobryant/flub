(ns trident.ion
  "Utilities for working with Datomic Ions"
  (:require [trident.util :as u]
            [datomic.ion.cast :as cast]
            [taoensso.timbre :as timbre]
            [datomic.ion :as ion]
            [clojure.walk :refer [keywordize-keys]]))

(defn ion-appender
  "A `taoensso.timbre` appender that sends logs to `datomic.ion.cast`."
  [{:keys [level vargs _output]}]
  (let [data (first vargs)]
    (when (and (map? data) (contains? data :msg))
      ((if (contains? #{:error :warn} level)
         cast/alert
         cast/dev)
       data))))

(defn set-timbre-ion-appender!
  "Enables [[ion-appender]]."
  []
  (timbre/merge-config!
    {:appenders {:println {:enabled? false}
                 :ion {:enabled? true
                       :fn ion-appender}}}))

(defprotocol Params
  "Intended for retrieving secrets."
  (get-params [config env])
  (get-path [config env])
  (get-param [config k]))

(def get-params-memoized
  (memoize
    (fn [this env]
      (keywordize-keys (ion/get-params {:path (get-path this env)})))))

(defrecord ParamClient [app-name env]
  Params
  (get-params [this env]
    (get-params-memoized this env))
  (get-path [this env]
    (format "/datomic-shared/%s/%s/" (name env) app-name))
  (get-param [this k]
    (->> [env :default]
         (map #(get (get-params this %) k))
         (filter some?)
         first)))

(defn default-config
  "Returns a client config map that's probably good enough for most people.

  If `system-name` is omited, it will be read from the `:app-name` key of your
  `datomic/ion-config.edn` file."
  ([system-name]
   {:system system-name
    :endpoint (str "http://entry." system-name ".us-east-1.datomic.net:8182/")
    :server-type :ion
    :region "us-east-1"
    :proxy-port 8182})
  ([]
   (default-config (:app-name (u/read-config "datomic/ion-config.edn")))))
