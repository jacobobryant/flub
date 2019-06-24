(ns trident.ion-dev.repl
  (:require [datomic.ion.cast :as cast]
            [immutant.web :as imm]
            [mount.core :as mount]))

(defmacro defhandler [sym config]
  `(mount/defstate ~sym
     :start (let [config# (merge {:port 8080} ~config)]
              (imm/run (:handler config#) {:port (:port config#)}))
     :stop (imm/stop)))

(cast/initialize-redirect :stdout)
