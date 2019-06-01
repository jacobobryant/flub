(ns trident.build.cli.deploy
  "Miscellaneous build tasks.
  See `trident.build` for usage."
  (:require [trident.build.cli :refer [defcli]]
            [trident.build.pom :refer [sync-pom]]
            [trident.build.jar :refer [jar]]
            [trident.build.lib :refer [cli-options jar-file]]
            [deps-deploy.deps-deploy :as deps-deploy]))

(defn- lib-task [command {:keys [skip-jar] :as opts}]
  (assert (contains? #{"install" "deploy"} command))
  (when (not skip-jar)
    (println "generating pom")
    (sync-pom opts)
    (println "packaging")
    (jar opts))
  (deps-deploy/-main command (jar-file opts)))
(def install (partial lib-task "install"))
(def deploy (partial lib-task "deploy"))

(defn- desc [x]
  [x "" "The jar path is `target/<artifact-id>-<version>.jar`"])

(defcli
  {:subcommands
   {"install" {:fn install
               :desc (desc "Installs a library to the local maven repo.")
               :config ["lib.edn"]
               :cli-options [:group-id :artifact-id :version :github-repo :skip-jar]}
    "deploy" {:fn deploy
              :desc (desc "Deploys a library to Clojars.")
              :config ["lib.edn"]
              :cli-options [:group-id :artifact-id :version :github-repo :skip-jar]}}}
  cli-options)
