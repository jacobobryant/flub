(ns trident.build.jar
  (:require [trident.cli :refer [make-cli expand-cli]]
            [trident.cli.util :refer [sh path]]
            [me.raynes.fs :as fs]
            [mach.pack.alpha.skinny :as skinny]
            [trident.build.lib :refer [cli-options jar-file]]))

(defn jar
  "Packages a jar at target/<artifact-id>-<version>.jar. pom.xml must exist already."
  [opts]
  ; TODO read artifact-id, version from pom
  (let [jar-file (jar-file opts)]
    (fs/delete jar-file)
    (fs/mkdirs "target/extra/META-INF/")
    (fs/copy "pom.xml" "target/extra/META-INF/pom.xml")
    (skinny/-main "--no-libs" "-e" (path "target/extra") "--project-path" jar-file)))

(let [{:keys [cli main-fn]}
      (make-cli
        {:fn #'jar
         :config ["lib.edn"]
         :cli-options [:artifact-id :version]}
        cli-options)]
  (def cli cli)
  (def -main main-fn))
