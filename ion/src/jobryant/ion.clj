(ns jobryant.ion
  (:require [datomic.ion.cast :as cast]
            [taoensso.timbre :as timbre]))

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
