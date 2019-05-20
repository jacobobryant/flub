(ns jobryant.trident-dev
  (:require [jobryant.util :as u]
            [clojure.tools.namespace.repl :as tn]
            [immutant.web :as imm]
            [nrepl.server :as nrepl]
            [jobryant.trident :as trident]
            [mount.core :as mount :refer [defstate]]
            [datomic.ion.cast :as cast]
            [orchestra.spec.test :as st]))

(u/defconfig
  {:port 8080
   :nrepl-port 7888})

; todo specify handler in config
(defn start-immutant []
  (imm/run
    trident/handler*
    {:port (:port config)}))

(defstate server :start (start-immutant)
                 :stop (imm/stop))

(defn go []
  (mount/start)
  (println :ready))

(defmacro reset []
  `(do (mount/stop)
       (tn/refresh :after 'jobryant.trident-dev/go)
       (use 'clojure.repl)))

(defn init []
  (st/instrument)
  (cast/initialize-redirect :stdout)
  (nrepl/start-server :port (:nrepl-port config))
  (go))
