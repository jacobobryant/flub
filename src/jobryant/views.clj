(ns jobryant.views)

(defn get-opts [opts contents]
  (if (map? opts)
    [opts contents]
    [nil (conj contents opts)]))

(defmacro defview [f [opts contents] & forms]
  `(defn ~f [opts# & contents#]
     (let [[~opts ~contents] (get-opts opts# contents#)]
       ~@forms)))
