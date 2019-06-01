(ns trident.build
  (:require [trident.build.cli :refer [defcli]]
            [trident.build.cli.mono :as mono]
            [trident.build.cli.cljdoc :as cljdoc]
            [trident.build.cli.pom :as pom]
            [trident.build.cli.jar :as jar]
            [trident.build.cli.deploy :as deploy]))

(defcli
  (mono/wrap-dir
    (merge
      {"mono" mono/-main
       "doc" cljdoc/-main
       "pom" pom/-main
       "jar" jar/-main}
      (:subcommands deploy/cli))))
