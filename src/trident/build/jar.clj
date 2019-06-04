(ns trident.build.jar
  (:require [trident.cli :refer [defcli reduce-cli]]
            [trident.cli.util :refer [sh path]]
            [mach.pack.alpha.skinny :as skinny]
            [trident.build.lib :refer [cli-options jar-file]]))

(defn jar
  "Packages a jar at target/<artifact-id>-<version>.jar. pom.xml must exist already."
  [opts]
  ; TODO read artifact-id, version from pom
  (let [jar-file (jar-file opts)]
    ; TODO use raynes
    (sh "rm" "-f" jar-file)
    (sh "mkdir" "-p" "target/extra/META-INF/")
    (sh "cp" "pom.xml" "target/extra/META-INF")
    (skinny/-main "--no-libs" "-e" (path "target/extra") "--project-path" jar-file)))

(defcli
  {:fn #'jar
   :config ["lib.edn"]
   :cli-options [:artifact-id :version]}
  cli-options)
