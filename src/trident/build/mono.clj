(ns trident.build.mono
  "Build tasks for working with projects defined by mono.edn

  See `trident.build` for usage."
  (:require [clojure.set :refer [union difference]]
            [trident.build.util :refer [sh abspath fexists? sppit with-dir]]
            [trident.build.cli :refer [dispatch]]
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

(defn mono [{:keys [projects group-id managed-deps] :as opts} & libs]
  (doseq [lib (or (not-empty (map symbol libs)) (conj (keys projects) group-id))]
    (let [dest (abspath "target" lib "src" group-id)
          [local-deps maven-deps] (if (= lib group-id)
                                    [(keys projects) (keys managed-deps)]
                                    (get-deps projects lib))
          maven-deps (conj maven-deps 'org.clojure/clojure)
          deps-edn (merge {:paths ["src"]
                           :deps (select-keys managed-deps maven-deps)}
                          (select-keys opts [:mvn/repos :aliases]))
          lib-edn (merge (select-keys opts [:version :group-id :github-repo :cljdoc-dir])
                         {:artifact-id (str lib)
                          :git-dir (abspath ".")})]
      ; TODO use me.raynes.fs
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

(defn main [commands {:keys [with-projects all-projects projects]} & args]
  (let [with-projects (if all-projects (keys projects) with-projects)
        run #(dispatch args commands)]
    (if with-projects
      (doseq [p with-projects]
        (with-dir (abspath "target" p)
          (run)))
      (run))))
