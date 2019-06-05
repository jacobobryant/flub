(ns trident.repl
  (:require [clojure.tools.namespace.repl :as tn]
            [nrepl.server :as nrepl]
            [mount.core :as mount]
            [orchestra.spec.test :as st]))

(defmacro reset []
  `(do (mount/stop)
       (tn/refresh :after 'trident.repl/init*)
       (use 'clojure.repl)))

(defn goto [sym]
  (doto sym require in-ns))

(defn init* []
  (mount/start)
  (println :ready))

(defn init
  ([] (init {}))
  ([{:keys [nrepl-port] :or {nrepl-port 7888}}]
   (st/instrument)
   (nrepl/start-server :port nrepl-port)
   (init*)

   (println "Run `(user/reset)` to reload all source changes.")
   (println "Run this if your repl gets borked after a `(user/reset)`:")
   (println)
   (println "  (require 'trident.repl)")
   (println "  (trident.repl/reset)")
   (println)))

(in-ns 'user)
(require 'trident.repl)
(defmacro reset []
  '(trident.repl/reset))
(def goto trident.repl/goto)
(in-ns 'trident.repl)
