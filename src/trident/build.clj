(ns trident.build
  (:require [trident.build.util :refer [sh abspath fexists? sppit with-dir]]
            [trident.build.cli :as cli]
            [trident.build.pom :as pom]
            [clojure.set :refer [union difference]]
            [clojure.pprint :refer [pprint]]
            [clojure.string :as str]
            [mach.pack.alpha.skinny :as skinny]
            [deps-deploy.deps-deploy :as deps-deploy]))

(defn- get-deps [projects lib]
  (let [local-deps (loop [deps #{}
                          libs #{lib}]
                     (if (empty? libs)
                       deps
                       (let [local-deps (set (mapcat #(get-in projects [% :local-deps]) libs))
                             deps (union deps libs)]
                         (recur
                           deps
                           (difference local-deps deps)))))
        maven-deps (set (mapcat #(get-in projects [% :deps]) local-deps))]
    [local-deps maven-deps]))

; todo move to a resources dir or something
(def ^:private build-contents
"#!/bin/bash
for f in ../../shared.sh project-build.sh; do
  if [ -f $f ]; then
    source $f
  fi
done
set -e
set -x
\"$@\"
")

(defn reset [{:keys [projects group-id managed-deps] :as opts} & libs]
  (doseq [lib (or (not-empty (map symbol libs)) (keys projects))]
    (let [dest (abspath "target" lib "src" group-id)
          [local-deps maven-deps] (get-deps projects lib)
          deps-edn (-> {:paths ["src"]
                        :deps (select-keys managed-deps maven-deps)}
                       (merge (select-keys opts [:mvn/repos :aliases]))
                       (update :mvn/repos assoc
                               "clojars" {:url "https://repo.clojars.org/"}
                               "central" {:url "https://repo1.maven.org/maven2"}))
          lib-edn (merge (select-keys opts [:version :group-id :github-repo :cljdoc-dir])
                         {:artifact-id (str lib)
                          :git-dir (abspath ".")})]
      (sh "rm" "-rf" dest)
      (sh "mkdir" "-p" dest)
      (doseq [dep local-deps
              ext [".clj" ".cljs" ".cljc" "/"]
              :let [dep (str/replace (name dep) "-" "_")
                    target (abspath "src" group-id (str dep ext))]
              :when (fexists? target)]
        (sh "ln" "-sr" target dest))
      (with-dir (abspath "target" lib)
        (sppit "deps.edn" deps-edn)
        (sppit "lib.edn" lib-edn)
        (spit "build" build-contents)
        (sh "chmod" "+x" "build")))))

(defn- jar-file [{:keys [artifact-id version]}]
  (abspath "target" (str artifact-id "-" version ".jar")))

(defn jar [opts]
  ; todo read artifact-id, version from pom
  (let [jar-file (jar-file opts)]
    (sh "rm" "-f" jar-file)
    (sh "mkdir" "-p" "target/extra/META-INF/")
    (sh "cp" "pom.xml" "target/extra/META-INF")
    (skinny/-main "--no-libs" "-e" (abspath "target/extra") "--project-path" jar-file))) ; ?

(defn- lib-task [command {:keys [skip-jar] :as opts}]
  (assert (contains? #{"install" "deploy"} command))
  (when (not skip-jar)
    (println "generating pom")
    (pom/sync-pom opts)
    (println "packaging")
    (jar opts)
    (println (str command "ing")))
  (deps-deploy/-main command (jar-file opts)))
(def install (partial lib-task "install"))
(def deploy (partial lib-task "deploy"))

; untested from clj
;(defn iondeploy [uname]
;  (let [push-out (sh "clojure" "-Adev" "-m" "datomic.ion.dev"
;                     (str {:op :push :uname uname}))
;        _ (print push-out)
;        deploy-cmd (get-in (read-string (str "[" push-out "]")) [1 :deploy-command])
;        deploy-out (sh "bash" "-c" deploy-cmd)
;        _ (print deploy-out)
;        status-cmd (:status-command (read-string deploy-out))]
;    ;todo auto quit
;    (while true
;      (print (sh "bash" "-c" status-cmd))
;      (sh "sleep" "5"))))

(defn cljdoc [{:keys [group-id artifact-id version cljdoc-dir git-dir remote-repo]}]
  (let [git-dir (abspath git-dir)
        args (cond-> ["./script/cljdoc" "ingest" "-p" (str group-id "/" artifact-id)
                      "-v" version]
               (not remote-repo) (concat ["--git" git-dir]))]
    (with-dir cljdoc-dir
      (print (apply sh args)))))

(declare commands)

(defn dispatch [opts]
  (cli/dispatch (merge opts {:commands commands})))

(defn main [{:keys [with-projects all-projects projects]} cmd & args]
  (let [with-projects (if all-projects (keys projects) with-projects)
        run #(dispatch {:cmd cmd :args args})]
    (if with-projects
      (doseq [p with-projects]
        (with-dir (abspath "target" p)
          (run)))
      (run))))

(defn -main [& args]
  (System/exit
    (dispatch {:cmd "main" :args args})))

(def jar-opts
  [["-a" "--artifact-id ID" "Artifact ID"]
   ["-v" "--version VERSION" "Version"]])

(def artifact-opts
  (into [["-g" "--group-id ID" "Group ID"]] jar-opts))

(def github-opt
  ["-G" "--github-repo REPO" "<username>/<repo>, e.g. `jacobobryant/trident`"])

(def pom-opts (conj artifact-opts github-opt))

(def deploy-opts
  (conj pom-opts ["-s" "--skip-jar" "Don't package jar first"]))

(defn append-opt [opt addendum]
  (update opt 2 #(str % addendum)))

(def commands
  {"main" {:fn main
           :defaults ["trident.edn"]
           :cli-options
           [["-w" "--with-projects PROJECTS"
             (str "Colon-separated list of projects. The subcommand will be "
                  "executed in `target/<project>` for each project.")
             :parse-fn #(str/split % #":")]
            ["-a" "--all-projects" (str "Execute the command in each project directory. "
                                        "Overrides --projects.")]]
           :subcommands ["reset" "doc" "pom" "jar" "install" "deploy"]}
   "reset" {:fn reset
            :defaults ["trident.edn"]}
   "doc" {:fn cljdoc
          :desc "Ingests a library into a locally running instance of cljdoc."
          :defaults ["lib.edn"]
          :cli-options
          (into artifact-opts
                [["-c" "--cljdoc-dir DIR" "The directory of the cloned cljdoc repo"]
                 ["-d" "--git-dir DIR"
                  (str "The top-level directory of the library's local git repo. "
                       "Used when --remote-repo isn't set.")
                  :default "."]
                 (append-opt github-opt ". Used when --remote-repo is set.")
                 ["-r" "--remote-repo" "Use remote repo instead of the local one"]])}
   "pom" {:fn pom/sync-pom
          :desc "Generates a new pom.xml in the current directory (overwrites any existing pom.xml)"
          :defaults ["lib.edn"]
          :cli-options pom-opts}
   "jar" {:fn jar
          :desc "Packages a jar at target/<artifact-id>-<version>.jar. pom.xml must exist already."
          :defaults ["lib.edn"]
          :cli-options jar-opts}
   "install" {:fn install
              :desc "Installs a library to the local maven repo."
              :defaults ["lib.edn"]
              :cli-options deploy-opts}
   "deploy" {:fn deploy
             :desc "Deploys a library to Clojars."
             :defaults ["lib.edn"]
             :cli-options deploy-opts}})
