(ns trident.build.xml
  (:require [clojure.zip :as zip]
            [clojure.data.zip.xml :as zipx]))

(defn xml-replace
  ([x k v]
   (-> x
       zip/xml-zip
       (zipx/xml1-> k)
       zip/down
       (zip/replace v)
       zip/root))
  ([x k v & kvs]
   (let [ret (xml-replace x k v)]
     (if kvs
       (if (next kvs)
         (recur ret (first kvs) (second kvs) (nnext kvs))
         (throw (IllegalArgumentException.
                  "xml-replace expects even number of arguments after map, found odd number")))
       ret))))
