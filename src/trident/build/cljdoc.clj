(ns trident.build.cljdoc
  (:require [trident.cli.util :refer [sh path]]
            [trident.cli :refer [make-cli]]
            [trident.build.lib :refer [cli-options]]))

(defn cljdoc
  "Ingests a library into a locally running instance of cljdoc.
  The library must be already installed to the local maven repo."
  [{:keys [group-id artifact-id version cljdoc-dir git-dir remote-repo]}]
  (let [args (cond-> ["./script/cljdoc" "ingest" "-p" (str group-id "/" artifact-id)
                      "-v" version]
               (not remote-repo) (concat ["--git" (path git-dir)])
               true (concat [:dir cljdoc-dir]))]
    (print (apply sh args))))

(let [cli-options (update-in cli-options [:github-repo 2]
                             str ". Used when --remote-repo is set.")
      {:keys [cli main-fn help]}
      (make-cli
        {:fn #'cljdoc
         :config ["lib.edn"]
         :cli-options [:group-id :artifact-id :version :cljdoc-dir
                       :git-dir :github-repo :remote-repo]
         :prog "clj -m trident.build.cljdoc"}
        cli-options)]
  (def cli cli)
  (def ^{:doc help} -main main-fn))
