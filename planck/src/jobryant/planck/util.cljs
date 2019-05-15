(ns jobryant.planck.util
  (:require [planck.shell :as shell]
            [clojure.string :refer [split join]]))

(defn sh [& args]
  (println (join " " (map pr-str args)))
  (let [result (apply shell/sh args)]
    (if (= 0 (:exit result))
      (:out result)
      (throw (ex-info (:err result) {})))))

(defn shsplit [cmd]
  (apply sh (split cmd #" ")))
