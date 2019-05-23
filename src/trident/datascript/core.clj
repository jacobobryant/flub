(ns trident.datascript.core)

(defmacro defq [sym & forms]
  `(def ~sym (register! (fn [] ~@forms))))
