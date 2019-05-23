(ns trident.build.util
  (:require [clojure.java.shell :as shell]
            [clojure.pprint :refer [pprint]]
            [clojure.string :refer [split join]]))

(defn fexists? [path]
  (= 0 (:exit (shell/sh "test" "-e" path))))

(defn basename [path]
  (last (split path #"/")))

(defn path [& xs]
  (join "/" xs))

(defn sh [& args]
  (let [result (apply shell/sh args)]
    (if (= 0 (:exit result))
      (:out result)
      (throw (ex-info (:err result) {})))))

(defn sppit [file x]
  (spit file (with-out-str (pprint x))))
