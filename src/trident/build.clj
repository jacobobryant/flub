(ns trident.build
  "A collection of build tasks defined using `trident.cli`."
  (:require [trident.cli :refer [make-cli]]
            [trident.build.mono :as mono]
            [trident.build.cljdoc :as cljdoc]
            [trident.build.pom :as pom]
            [trident.build.jar :as jar]
            [trident.build.deploy :as deploy]))


(let [{:keys [cli main-fn help]}
      (make-cli
        (assoc
          (mono/wrap-dir
            (merge
              {"mono" mono/cli
               "doc" cljdoc/cli
               "pom" pom/cli
               "jar" jar/cli}
              (:subcommands deploy/cli)))
          :prog "clj -m trident.build"))]
  (def cli cli)
  (def ^{:doc help} -main main-fn))
