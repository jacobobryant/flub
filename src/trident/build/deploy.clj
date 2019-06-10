(ns trident.build.deploy
  (:require [trident.cli :refer [help main-fn]]
            [trident.build.pom :refer [sync-pom]]
            [trident.build.jar :refer [jar]]
            [trident.build.lib :refer [cli-options jar-file]]
            [deps-deploy.deps-deploy :as deps-deploy]
            [clojure.string :as str]))

(defn ^:no-doc deps-task [command {:keys [skip-jar] :as opts}]
  (assert (contains? #{"install" "deploy"} command))
  (when (not skip-jar)
    (println "generating pom")
    (sync-pom opts)
    (println "packaging")
    (jar opts))
  (deps-deploy/-main command (jar-file opts)))

(defn ^:no-doc subcommand [cmd desc]
  {:fn (partial deps-task cmd)
   :prog (str "clj -m trident.build.deploy " cmd)
   :desc (conj desc (str "Packages the jar first by default. The jar path is "
                         "`target/<artifact-id>-<version>.jar`."))
   :config :trident/lib
   :options cli-options
   :option-keys [:group-id :artifact-id :version :github-repo :skip-jar]})

(let [install-cmd (subcommand "install" ["Installs a library to the local maven repo."])
      deploy-cmd (subcommand "deploy"
                             ["Deploys a library to Clojars."
                              (str "The environment variables `CLOJARS_USERNAME` and "
                                   "`CLOJARS_PASSWORD` must be set.")])]
  (def cli {:prog "clj -m trident.build.deploy"
            :subcommands {"install" install-cmd "deploy" deploy-cmd}})

  (def ^{:doc (str/join "\n\n" (map help [cli install-cmd deploy-cmd]))}
    -main (main-fn cli)))
