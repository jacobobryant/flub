(ns trident.build.util
  (:require [clojure.java.shell :as shell]
            [clojure.pprint :refer [pprint]]
            [clojure.string :refer [split join starts-with? trim]]))

(defn fexists? [path]
  (= 0 (:exit (shell/sh "test" "-e" path))))

(defn basename [path]
  (last (split path #"/")))

(defn sh [& args]
  (let [result (apply shell/sh args)]
    (if (= 0 (:exit result))
      (:out result)
      (throw (ex-info (:err result) result)))))

(defn cwd []
  (trim (sh "pwd")))

(defn path [& xs]
  (join "/"
        (if (starts-with? (first xs) "/")
          xs
          (conj xs (cwd)))))

(defn sppit [file x]
  (spit file (with-out-str (pprint x))))
