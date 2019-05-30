(ns trident.build.util
  (:require [clojure.java.shell :as shell]
            [clojure.pprint :refer [pprint]]
            [clojure.string :refer [split join starts-with? trim]]
            [me.raynes.fs :as raynes :refer [absolute]])
  (:import (jnr.posix POSIXFactory)))

(defn fexists?
  "Returns true if `path` exists on the filesystem"
  [path]
  (= 0 (:exit (shell/sh "test" "-e" path))))

(defn sh
  "Runs a shell command.

  Returns the output if successful; otherwise, throws an exception."
  [& args]
  (let [result (apply shell/sh args)]
    (if (= 0 (:exit result))
      (:out result)
      (throw (ex-info (:err result) result)))))

(defn abspath
  "Joins `xs`, returning an absolute path."
  [& xs]
  (.getPath (absolute (join "/" xs))))

(defn sppit
  "pprints `x` to `file`"
  [file x]
  (spit file (with-out-str (pprint x))))

(defn maybe-slurp
  "Attemps to slurp `f`, returns nil on failure"
  [f]
  (try
    (slurp f)
    (catch Exception e nil)))

(let [posix (delay (POSIXFactory/getNativePOSIX))]
  (defn chdir
    "Uses the Java Native Runtime to **actually** change the current working directory.

    Also updates the \"user.dir\" system property."
    [dir]
    (let [dir (abspath dir)]
      (.chdir @posix dir)
      (System/setProperty "user.dir" dir))))

(defn with-dir*
  "Runs `(f)` in the given directory.

  See [[with-dir]]."
  [new-dir f]
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

(defmacro with-dir
  "Runs `forms` in the given directory.

  Changes the directory using several methods:
   - `clojure.java.shell/with-sh-dir`
   - `me.raynes.fs/with-cwd`
   - [[chdir]]"
  [dir & forms]
  `(with-dir* ~dir #(do ~@forms)))
