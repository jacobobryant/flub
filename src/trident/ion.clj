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

(u/defconfig
  {:path (fn [env] (format "/datomic-shared/%s/%s/"
                           env (:app-name config)))})

(def ^{:doc "Wrapper for `datomic.ion/get-params`"}
  get-params (memoize #(-> {:path ((:path config) %)}
                           ion/get-params
                           keywordize-keys)))

(defn get-param
  "Wrapper for [[get-params]]."
  [k]
  (->> [(name (:env config)) "default"]
       (map #(get (get-params %) k))
       (filter some?)
       first))
