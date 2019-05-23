(ns trident.build
  (:require [clojure.java.shell :as shell :refer [with-sh-dir]]
            [trident.build.util :refer [sh path fexists? sppit]]
            [trident.build.pom :as pom]
            [clojure.set :refer [union difference]]
            [clojure.string :as str]
            [mach.pack.alpha.skinny :as skinny]
            [deps-deploy.deps-deploy :as deps-deploy]))

(def ^:private config (memoize #(read-string (slurp "trident.edn"))))

(declare dispatch)

(defn- get-libs [libs]
  (or (not-empty (map symbol libs)) (-> (config) :artifacts keys)))

(defn- get-deps [lib]
  (let [local-deps (loop [deps #{}
                          libs #{lib}]
                     (if (empty? libs)
                       deps
                       (let [local-deps (set (mapcat #(get-in (config) [:artifacts % :local-deps]) libs))
                             deps (union deps libs)]
                         (recur
                           deps
                           (difference local-deps deps)))))
        maven-deps (set (mapcat #(get-in (config) [:artifacts % :deps]) local-deps))]
    [local-deps maven-deps]))

(def ^:private build-contents
"#!/bin/bash
for f in ../../shared.sh project-build.sh; do
  if [ -f ../../shared.sh ]; then
    source ../../shared.sh
  fi
done
set -e
set -x
\"$@\"
")

(defn reset [& libs]
  (doseq [lib (get-libs libs)]
    (let [{:keys [group managed-deps]} (config)]
      (let [dest (path "projects" lib "src" group)
            [local-deps maven-deps] (get-deps lib)
            deps-edn (merge {:paths ["src"]
                             :deps (select-keys managed-deps maven-deps)}
                            (select-keys (config) [:mvn/repos :aliases]))
            lib-edn (merge (select-keys (config) [:version :group :github-repo])
                           {:artifact (str lib)})]
        (sh "rm" "-rf" dest)
        (sh "mkdir" "-p" dest)
        (doseq [dep local-deps
                ext [".clj" ".cljs" ".cljc" "/"]
                :let [dep (str/replace (name dep) "-" "_")
                      target (str "src/" group "/" dep ext)]
                :when (fexists? target)]
          (sh "ln" "-sr" target dest))
        (sppit (path "projects" lib "deps.edn") deps-edn)
        (sppit (path "projects" lib "lib.edn") lib-edn)
        (spit (path "projects" lib "build") build-contents)
        (sh "chmod" "+x" (path "projects" lib "build"))))))

(let [cache (memoize (fn [_] (read-string (sh "cat" "lib.edn"))))]
  (defn- lib-config []
    (cache (sh "pwd"))))

(defn- jar-file []
  (let [{:keys [artifact version]} (lib-config)]
    (str "projects/" artifact "-" version ".jar")))

(defn pom []
  (pom/-main (sh "cat" "lib.edn")))

(defn jar []
  (sh "rm" "-f" (jar-file))
  (skinny/-main "--no-libs" "--project-path" (jar-file)))

(defn- lib-task [command & [fast?]]
  (assert (contains? #{"install" "deploy"} command))
  (when (not= fast? "fast")
    (println "packaging")
    (jar)
    (println "generating pom")
    (pom)
    (println (str command "ing")))
  (deps-deploy/-main command (jar-file)))
(def install (partial lib-task "install"))
(def deploy (partial lib-task "deploy"))

(defn iondeploy [uname]
  (let [push-out (sh "clojure" "-Adev" "-m" "datomic.ion.dev"
                     (str {:op :push :uname uname}))
        _ (print push-out)
        deploy-cmd (get-in (read-string (str "[" push-out "]")) [1 :deploy-command])
        deploy-out (sh "bash" "-c" deploy-cmd)
        _ (print deploy-out)
        status-cmd (:status-command (read-string deploy-out))]
    ;todo auto quit
    (while true
      (print (sh "bash" "-c" status-cmd))
      (sh "sleep" "5"))))

;(defn doc [lib]
;  (let [git-dir (path (sh "pwd") "projects" lib)]
;    (with-sh-dir (:cljdoc-dir (config))
;      (print (sh "./script/cljdoc" "ingest" "-p" (str (:group (config)) "/" lib)
;                 "-v" (:version (config)) "--git" git-dir)))))

(defn forall [f & libs]
  (doseq [lib (get-libs libs)]
    (with-sh-dir (str "projects/" lib)
      (dispatch f))))

(defn- dispatch [f & args]
  (apply ((ns-publics 'trident.build) (symbol f)) args))

(defn -main [& args]
  (apply dispatch args)
  (shutdown-agents))
