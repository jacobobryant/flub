(ns trident.build
  "A collection of build tasks defined using `trident.cli`."
  (:require [trident.cli :refer [defmain]]
            [trident.build.mono :as mono]
            [trident.build.cljdoc :as cljdoc]
            [trident.build.pom :as pom]
            [trident.build.jar :as jar]
            [trident.build.deploy :as deploy]))

(def cli
  (assoc
    (mono/with-mono-options
      (merge
        {"mono" mono/cli
         "doc" cljdoc/cli
         "pom" pom/cli
         "jar" jar/cli}
        (:subcommands deploy/cli)))
    :prog "clj -m trident.build"))

(defmain cli)
