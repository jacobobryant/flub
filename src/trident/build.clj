(ns trident.build
  (:require [trident.cli :refer [defcli]]
            [trident.build.mono :as mono]
            [trident.build.cljdoc :as cljdoc]
            [trident.build.pom :as pom]
            [trident.build.jar :as jar]
            [trident.build.deploy :as deploy]))

(defcli
  (mono/wrap-dir
    (merge
      {"mono" mono/cli
       "doc" cljdoc/cli
       "pom" pom/cli
       "jar" jar/cli}
      (:subcommands deploy/cli))))
