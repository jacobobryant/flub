(ns util.core
  (:require [planck.shell :as shell]
            [planck.core :refer [spit]]
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

(defn shsplit [cmd]
  (apply sh (split cmd #" ")))

(defn sppit [file x]
  (spit file (with-out-str (pprint x))))
