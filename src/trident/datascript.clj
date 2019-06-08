(ns trident.datascript)

(defmacro defq
  "Defines a reactive atom backed by a Datascript query.

  `sym`, the reactive atom, will contain the result of `forms`. `sym` will be
  updated whenever [[transact!]] or [[init-from-datomic!]] are called.

  Although it's intended that `forms` contains a Datascript query, you can put
  anything in there."
  [sym & forms]
  `(def ~sym (register! (fn [] ~@forms))))
