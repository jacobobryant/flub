(ns trident.build.jar
  (:require [trident.cli :refer [defmain]]
            [trident.cli.util :refer [sh path]]
            [me.raynes.fs :as fs]
            [mach.pack.alpha.skinny :as skinny]
            [trident.build.lib :refer [cli-options jar-file]]))

(defn jar
  "Packages a jar at `target/<artifact-id>-<version>.jar`.
  `pom.xml` must exist already."
  [opts]
  ; TODO read artifact-id, version from pom
  (let [jar-file (jar-file opts)]
    (fs/delete jar-file)
    (fs/mkdirs "target/extra/META-INF/")
    (fs/copy "pom.xml" "target/extra/META-INF/pom.xml")
    (skinny/-main "--no-libs" "-e" (path "target/extra") "--project-path" jar-file)))

(def cli
  {:fn #'jar
   :prog "clj -m trident.build.jar"
   :config :trident/lib
   :options cli-options
   :option-keys [:artifact-id :version]})

(defmain cli)
