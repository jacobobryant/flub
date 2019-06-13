(ns trident.web-repl
  "A dev web server for use with [[trident.web]]."
  (:require [trident.util :as u]
            [immutant.web :as imm]
            [trident.web :as web]
            [mount.core :as mount :refer [defstate]]
            [datomic.ion.cast :as cast]))

(u/defconfig {:port 8080})

; todo specify handler in config
(defn start-immutant []
  (imm/run
    web/handler*
    {:port (:port config)}))

(defstate server :start (start-immutant)
                 :stop (imm/stop))

(cast/initialize-redirect :stdout)
