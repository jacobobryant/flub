(ns trident.repl)

(in-ns 'user)
(require '[clojure.tools.namespace.repl :as tn])
(require '[nrepl.server :as nrepl])
(require '[mount.core :as mount])
(require '[orchestra.spec.test :as st])

(defn go []
  (mount/start)
  (println :ready))

(defmacro reset []
  `(do (mount/stop)
       (tn/refresh :after 'user/go)
       (use 'clojure.repl)))

(defn init
  ([] (init {}))
  ([{:keys [nrepl-port] :or {nrepl-port 7888}}]
   (st/instrument)
   (nrepl/start-server :port nrepl-port)
   (go)))

(defn goto [sym]
  (doto sym require in-ns))

(in-ns 'trident.repl)

(comment
  (require '[clojure.tools.namespace.repl :as tn])
  (tn/refresh)
  )
