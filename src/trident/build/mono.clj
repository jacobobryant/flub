(ns trident.build.mono
  "Build tasks for working with projects defined by mono.edn"
  (:require [clojure.set :refer [union difference]]
            [trident.cli :refer [make-cli expand-cli]]
            [trident.cli.util :refer [sh path fexists? sppit with-dir]]
            [clojure.string :as str]))

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

; TODO move to a resources dir or something
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

(defn mono
  "Creates project directories according to mono.edn.
  If no projects are specified, operate on all projects."
  [{:keys [projects group-id managed-deps] :as opts} & libs]
  (doseq [lib (or (not-empty (map symbol libs)) (conj (keys projects) group-id))]
    (let [dest (path "target" lib "src" group-id)
          [local-deps maven-deps] (if (= lib group-id)
                                    [(keys projects) (keys managed-deps)]
                                    (get-deps projects lib))
          maven-deps (conj maven-deps 'org.clojure/clojure)
          deps-edn (merge {:paths ["src"]
                           :deps (select-keys managed-deps maven-deps)}
                          (select-keys opts [:mvn/repos :aliases]))
          lib-edn (merge (select-keys opts [:version :group-id :github-repo :cljdoc-dir])
                         {:artifact-id (str lib)
                          :git-dir (path ".")})]
      ; TODO use me.raynes.fs
      (sh "rm" "-rf" dest)
      (sh "mkdir" "-p" dest)
      (doseq [dep local-deps
              ext [".clj" ".cljs" ".cljc" "/"]
              :let [dep (str/replace (name dep) "-" "_")
                    target (path "src" group-id (str dep ext))]
              :when (fexists? target)]
        (sh "ln" "-sr" target dest))
      (with-dir (path "target" lib)
        (sppit "deps.edn" deps-edn)
        (sppit "lib.edn" lib-edn)
        (spit "build" build-contents)
        (sh "chmod" "+x" "build")))))

(let [{:keys [cli main-fn]}
      (make-cli
        {:fn #'mono
         :args-desc "[<project(s)>]"
         :config ["mono.edn"]})]
  (def cli cli)
  (def -main main-fn))

(defn- wrap-dir* [{:keys [with-projects all-projects projects]} subcommand]
  (if-some [with-projects (if all-projects (keys projects) with-projects)]
    (doseq [p with-projects]
      (with-dir (path "target" p)
        (subcommand)))
    (subcommand)))

(def cli-options
  {:with-projects ["-w" "PROJECTS" (str "Colon-separated list of projects. The subcommand will "
                                        "be executed in `target/<project>` for each project.")
                   :parse-fn #(str/split % #":")]
   :all-projects  ["-a" nil (str "Execute the command in each project directory (as defined by"
                                 " mono.edn). Overrides --with-projects.")]})

(defn wrap-dir [subcommands]
  (expand-cli
    {:wrap wrap-dir*
     :config ["mono.edn"]
     :cli-options [:with-projects :all-projects]
     :subcommands subcommands}
    cli-options))
