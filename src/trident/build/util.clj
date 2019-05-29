(ns trident.build.util
  (:require [clojure.java.shell :as shell]
            [clojure.pprint :refer [pprint]]
            [clojure.string :refer [split join starts-with? trim]]
            [me.raynes.fs :as raynes :refer [absolute]])
  (:import (jnr.posix POSIXFactory)))

(defn fexists? [path]
  (= 0 (:exit (shell/sh "test" "-e" path))))

(defn basename [path]
  (last (split path #"/")))

(defn sh [& args]
  (let [result (apply shell/sh args)]
    (if (= 0 (:exit result))
      (:out result)
      (throw (ex-info (:err result) result)))))

(defn abspath [& xs]
  (.getPath (absolute (join "/" xs))))

(defn sppit [file x]
  (spit file (with-out-str (pprint x))))

(defn maybe-slurp [f]
  (try
    (slurp f)
    (catch Exception e nil)))

(let [posix (delay (POSIXFactory/getNativePOSIX))]
  (defn chdir [dir]
    (let [dir (abspath dir)]
      (.chdir @posix dir)
      (System/setProperty "user.dir" dir))))

(defn with-dir* [new-dir f]
  (let [new-dir (abspath new-dir)
        old-dir (System/getProperty "user.dir")
        _ (chdir new-dir)
        result (shell/with-sh-dir new-dir
                 (raynes/with-cwd new-dir
                   (try
                     {:success (f)}
                     (catch Exception e
                       {:fail e}))))]
    (chdir old-dir)
    (if (contains? result :success)
      (:success result)
      (throw (:fail result)))))

(defmacro with-dir [dir & forms]
  `(with-dir* ~dir #(do ~@forms)))
