(ns jobryant.ion
  (:require [jobryant.util :as u]
            [datomic.ion.cast :as cast]
            [taoensso.timbre :as timbre]
            [datomic.ion :as ion]
            [clojure.walk :refer [keywordize-keys]]))

(defn ion-appender [{:keys [level vargs _output]}]
  (let [data (first vargs)]
    (when (and (map? data) (contains? data :msg))
      ((if (contains? #{:error :warn} level)
         cast/alert
         cast/dev)
       data))))

(defn set-timbre-ion-appender! []
  (timbre/merge-config!
    {:appenders {:println {:enabled? false}
                 :ion {:enabled? true
                       :fn ion-appender}}}))

(u/defconfig
  {:path (fn [env] (format "/datomic-shared/%s/%s/"
                           env (:app-name config)))})

(def get-params (memoize #(-> {:path ((:path config) %)}
                              ion/get-params
                              keywordize-keys)))

(defn get-param [k]
  (->> [(name (:env config)) "default"]
       (map #(get (get-params %) k))
       (filter some?)
       first))
