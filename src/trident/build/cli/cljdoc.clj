(ns trident.build.cli.cljdoc
  (:require [trident.build.util :refer [path]]
            [trident.build.cli :refer [defcli]]
            [trident.build.lib :refer [cli-options]]))

(defn cljdoc [{:keys [group-id artifact-id version cljdoc-dir git-dir remote-repo]}]
  (let [args (cond-> ["./script/cljdoc" "ingest" "-p" (str group-id "/" artifact-id)
                      "-v" version]
               (not remote-repo) (concat ["--git" (path git-dir)])
               true (concat [:dir cljdoc-dir]))]
    (print (apply sh args))))

(defcli
  {:fn cljdoc
   :desc "Ingests a library into a locally running instance of cljdoc."
   :config ["lib.edn"]
   :cli-options [:group-id :artifact-id :version :cljdoc-dir
                 :git-dir :github-repo :remote-repo]
   :append {:github-repo ". Used when --remote-repo is set."}}
  cli-options)
