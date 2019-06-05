(ns trident.build.deploy
  "Miscellaneous build tasks.
  See `trident.build` for usage."
  (:require [trident.cli :refer [make-cli]]
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

(let [subcommand (fn [f desc]
                   {:fn f
                    :desc [desc (str "Packages the jar first by default. The jar path is "
                                     "`target/<artifact-id>-<version>.jar`.")]
                    :config ["lib.edn"]
                    :cli-options [:group-id :artifact-id :version :github-repo :skip-jar]})

      {:keys [cli main-fn]}
      (make-cli
        {:subcommands
         {"install" (subcommand install "Installs a library to the local maven repo.")
          "deploy" (subcommand deploy "Deploys a library to Clojars.")}}
        cli-options)]
  (def cli cli)
  (def -main main-fn))
