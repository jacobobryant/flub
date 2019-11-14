(ns trident.cli.util
  (:require [clojure.java.shell :as shell]
            [clojure.pprint :refer [pprint]]
            [clojure.string :refer [split join starts-with? trim]]
            [trident.util :as u]
            [clojure.java.io :as io]
            [me.raynes.fs :as fs :refer [absolute]])
  (:import jnr.posix.POSIXFactory
           java.nio.file.LinkOption))

(def ^{:doc "Deprecated: use [[trident.util/sh]]"} sh u/sh)

(defn path
  "Joins `xs`, returning an absolute path. Respects the \"user.dir\" property."
  [& xs]
  (.getAbsolutePath (java.io.File. (join "/" xs))))

(defn sppit
  "pprints `x` to `file`"
  [file x]
  (spit file (with-out-str (pprint x))))

(defn rmrf
  "Delete recursively without following symlinks, like `rm -rf`"
  [f]
  (fs/delete-dir f LinkOption/NOFOLLOW_LINKS))

(let [posix (delay (POSIXFactory/getNativePOSIX))]
  (defn chdir
    "Uses the Java Native Runtime to **actually** change the current working directory.
    Also updates the \"user.dir\" system property."
    [dir]
    (let [dir (path dir)]
      (.chdir @posix dir)
      (System/setProperty "user.dir" dir))))

(defn with-dir*
  "Calls `f` in the given directory.
  See [[with-dir]]."
  [new-dir f]
  (let [new-dir (path new-dir)
        old-dir (System/getProperty "user.dir")
        _ (chdir new-dir)
        result (shell/with-sh-dir new-dir
                 (fs/with-cwd new-dir
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
   - [[chdir]]

  This is NOT thread-safe: the directory will be changed for the entire
  process while the forms are running."
  [dir & forms]
  `(with-dir* ~dir #(do ~@forms)))

(def ^:no-doc no-exit-sm
  (proxy [SecurityManager] []
    (checkPermission
      [^java.security.Permission p]
      (when (.startsWith (.getName p) "exitVM")
        (->> (count "exitVM.")
             (subs (.getName p))
             (Integer/parseInt)
             (hash-map ::exit-code)
             (ex-info "")
             (throw))))))

(defn with-no-shutdown*
  "Calls `f`, preventing calls to `System/exit` or `shutdown-agents`."
  [f]
  (let [old-sm (System/getSecurityManager)
        _ (System/setSecurityManager no-exit-sm)
        [success? result] (with-redefs [shutdown-agents (constantly nil)]
                            (try [true (f)]
                                 (catch Exception e [false e])))]
    (System/setSecurityManager old-sm)
    (if success?
      result
      (or (::exit-code (ex-data result))
          (throw result)))))

(defmacro with-no-shutdown
  "Runs forms, preventing calls to `System/exit` or `shutdown-agents`."
  [& forms]
  `(with-no-shutdown* (fn [] ~@forms)))

(defn -main
  "Launch other commands using [[with-no-shutdown]] and [[with-dir]]."
  [dir entry-point & args]
  (let [f (u/load-var (symbol entry-point))]
    (with-no-shutdown
      (with-dir dir
        (apply f args)))))
