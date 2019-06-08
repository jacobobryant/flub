(ns trident.views.re-com
  (:refer-clojure :exclude [for]))

(defmacro for
  "Like `for`, but avoids React's complaints about unique keys."
  [bindings body]
  `(clojure.core/for ~bindings
     (with-meta ~body {:key ~(first bindings)})))
