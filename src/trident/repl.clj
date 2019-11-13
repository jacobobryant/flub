(ns trident.repl
  "Convenience functions for working at the repl."
  (:require [clojure.tools.namespace.repl :as tn]
            [nrepl.server :as nrepl]
            [immutant.web :as imm]
            [mount.core :as mount]
            [orchestra.spec.test :as st]))

(defmacro refresh
  "Refreshes namespaces and calls `(use 'clojure.repl)` afterwards.

  `args`: passed to `clojure.tools.namespace.repl/refresh`, e.g.
  `[:after 'mount.core/start]`.

  Recommended usage: define a `refresh` macro in the `user` namespace that
  delegates to this."
  [& args]
  `(do (tn/refresh ~@args)
       (use 'clojure.repl)
       :ready))

(defn init
  "One-time init code for calling after opening the repl."
  ([] (init {}))
  ([{:keys [nrepl-port] :or {nrepl-port 7888}}]
   (st/instrument)
   (nrepl/start-server :port nrepl-port)
   (println "Started nrepl server on port" nrepl-port)))

(defmacro defhandler [sym config]
  `(mount/defstate ~sym
     :start (let [config# (merge {:port 8080} ~config)]
              (imm/run (:handler config#) {:port (:port config#)}))
     :stop (imm/stop)))
