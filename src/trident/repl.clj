(ns trident.repl
  "Convenience functions for working at the repl.

  Suggested usage:
   - Include `trident.repl` in the `extra-deps` of your `:dev` alias
   - Launch a repl with this command:
     `clj -Adev -e \"(do (require 'trident.repl) (trident.repl/init))\" -r`

  This will:
   - begin spec instrumentation with `orchestra.spec.test/instrument`
   - start an nRepl server (on port 7888 by default)
   - load all namespaces visible to `clojure.tools.namespace.repl/refresh`
   - call `mount.core/start`
   - add an alias to [[reset]] in the `user` namespace"
  (:require [clojure.tools.namespace.repl :as tn]
            [nrepl.server :as nrepl]
            [mount.core :as mount]
            [orchestra.spec.test :as st]))

(defn reset*
  "Reloads namespaces, starting and stopping mount components"
  ([] (reset* {}))
  ([{:keys [mount?] :or {mount? true}}]
   (do (mount/stop)
       (if mount?
         (tn/refresh :after 'mount.core/start)
         (tn/refresh)))))

(defmacro reset
  "Reloads namespaces, starting and stopping mount components"
  [& args]
  `(do (reset* ~@args)
       (use 'clojure.repl)
       :ready))

(defn init
  ([] (init {}))
  ([{:keys [nrepl-port] :or {nrepl-port 7888}}]
   (st/instrument)
   (nrepl/start-server :port nrepl-port)
   (reset {})

   (println "Run `(user/reset)` to reload all source changes.")
   (println "Run this if your repl gets borked after a `(user/reset)`:")
   (println)
   (println "  (do (require 'trident.repl) (trident.repl/reset))")
   (println)))

(in-ns 'user)
(require 'trident.repl)
(defmacro reset [& args]
  `(trident.repl/reset ~@args))
(in-ns 'trident.repl)
