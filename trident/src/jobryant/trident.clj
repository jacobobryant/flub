(ns jobryant.trident
  (:require [jobryant.util :as u]
            [jobryant.datomic-cloud.txauth :as txauth]
            [jobryant.firebase :refer [verify-token]]
            [jobryant.ion :refer [set-timbre-ion-appender!]]
            [compojure.core :refer [defroutes GET POST]]
            [datomic.ion.lambda.api-gateway :refer [ionize]]))

(defn init! []
  (set-timbre-ion-appender!))

(defmacro defhandlers [routes config]
  `(do
     (def ~'handler* (u/wrap-ion-defaults ~routes ~config))
     (def ~'handler (ionize ~'handler*))))
