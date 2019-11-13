(ns trident.repl
  "Convenience functions for working at the repl."
  (:require [clojure.tools.namespace.repl :as tn]
            [nrepl.server :as nrepl]
            [immutant.web :as imm]
            [mount.core :as mount]
            [orchestra.spec.test :as st]))

(defmacro refresh
  "Refreshes namespaces and calls `(use 'clojure.repl)` afterwards.

  Restarts mount components."
  []
  `(do (mount/stop)
       (tn/refresh :after 'mount.core/start)
       (use 'clojure.repl)
       :ready))

(defn init
  "One-time init code for calling after opening the repl."
  ([] (init {}))
  ([{:keys [nrepl-port] :or {nrepl-port 7888}}]
   (st/instrument)
   (nrepl/start-server :port nrepl-port)
   (println "Started nrepl server on port" nrepl-port)
   (refresh)))

(defmacro defhandler [sym config]
  `(mount/defstate ~sym
     :start (let [config# (merge {:port 8080} ~config)]
              (imm/run (:handler config#) {:port (:port config#)}))
     :stop (imm/stop)))
