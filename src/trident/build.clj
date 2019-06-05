(ns trident.build
  (:require [trident.cli :refer [make-cli]]
            [trident.build.mono :as mono]
            [trident.build.cljdoc :as cljdoc]
            [trident.build.pom :as pom]
            [trident.build.jar :as jar]
            [trident.build.deploy :as deploy]))

(let [{:keys [cli main-fn]}
      (make-cli
        (mono/wrap-dir
          (merge
            {"mono" mono/cli
             "doc" cljdoc/cli
             "pom" pom/cli
             "jar" jar/cli}
            (:subcommands deploy/cli))))]
  (def cli cli)
  (def -main main-fn))
