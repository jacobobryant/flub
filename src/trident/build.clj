(ns trident.build
  (:require [trident.build.cli :refer [dispatch defcmds]]
            [trident.build.pom :as pom]
            [trident.build.mono :as mono]
            [trident.build.misc :as misc]
            [clojure.string :refer [split]]))

(def cli-options
  {:artifact-id   ["-a" "ID"]
   :version       ["-v" "VERSION"]
   :group-id      ["-g" "ID"]
   :github-repo   ["-G" "REPO" "<username>/<repo>, e.g. `jacobobryant/trident`"]
   :skip-jar      ["-s" nil "Don't package jar first"]
   :cljdoc-dir    ["-c" "DIR" "The directory of the cloned cljdoc repo"]
   :git-dir       ["-d" "DIR" (str "The location of the library's local git repo. "
                                   "Used when --remote-repo isn't set.")
                   :default "."]
   :remote-repo   ["-r" nil "Use remote repo instead of the local one"]
   :with-projects ["-w" "PROJECTS" (str "Colon-separated list of projects. The subcommand will "
                                        "be executed in `target/<project>` for each project.")
                   :parse-fn #(split % #":")]
   :all-projects  ["-a" nil (str "Execute the command in each project directory. "
                                 "Overrides --with-projects.")]})

; TODO derive :cli-options from a fn spec?
(defcmds subcommands cli-options
  {"mono" {:fn mono/mono
           :desc ["Creates project directories according to mono.edn."
                  "If no projects are specified, operate on all projects."]
           :args-desc "[<project(s)>]"
           :config ["mono.edn"]}
   "doc" {:fn misc/cljdoc
          :desc "Ingests a library into a locally running instance of cljdoc."
          :config ["lib.edn"]
          :cli-options [:group-id :artifact-id :version :cljdoc-dir
                        :git-dir :github-repo :remote-repo]
          :append {:github-repo ". Used when --remote-repo is set."}}
   "pom" {:fn pom/sync-pom
          :desc "Generates a new pom.xml in the current directory (overwrites any existing pom.xml)."
          :config ["lib.edn"]
          :cli-options [:group-id :artifact-id :version :github-repo]}
   "jar" {:fn misc/jar
          :desc "Packages a jar at target/<artifact-id>-<version>.jar. pom.xml must exist already."
          :config ["lib.edn"]
          :cli-options [:artifact-id :version]}
   "install" {:fn misc/install
              :desc "Installs a library to the local maven repo."
              :config ["lib.edn"]
              :cli-options [:group-id :artifact-id :version :github-repo :skip-jar]}
   "deploy" {:fn misc/deploy
             :desc "Deploys a library to Clojars."
             :config ["lib.edn"]
             :cli-options [:group-id :artifact-id :version :github-repo :skip-jar]}})

(defcmds commands cli-options
  {"main" {:fn (partial mono/main subcommands)
           :config ["mono.edn"]
           :cli-options [:with-projects :all-projects]
           :subcommands subcommands}})

(defn -main [& args]
  (System/exit (dispatch (into ["main"] args) commands)))
