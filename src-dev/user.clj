(ns user
  (:require [trident.repl :as repl]))

(def init repl/init)
(defmacro refresh [] `(repl/refresh nil))
