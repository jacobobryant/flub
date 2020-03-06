(ns trident.firestore)

(defmacro write
  [& args]
  `((fn [] (trident.firestore.util/write-unsafe ~@args))))
