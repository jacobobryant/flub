(ns user
  (:require [trident.repl :as repl]
            [clojure.test :as t]))

(defmacro refresh [] `(repl/refresh))
(defn init [] (repl/init {:nrepl-port 7800}) (refresh))
