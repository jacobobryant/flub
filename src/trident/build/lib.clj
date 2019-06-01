(ns trident.build.lib
  (:require [trident.build.util :refer [path]]))

(def cli-options
  {:artifact-id   ["-a" "ID"]
   :version       ["-v" "VERSION"]
   :group-id      ["-g" "ID"]
   :github-repo   ["-G" "REPO" "<username>/<repo>, e.g. `jacobobryant/trident`"]
   :skip-jar      ["-s" nil "Don't package jar first"]
   :cljdoc-dir    ["-c" "DIR" "The directory of the cloned cljdoc repo"]
   :git-dir       ["-d" "DIR" (str "The location of the library's local git repo. "
                                   "Used when --remote-repo isn't set.")
                   :default "."]
   :remote-repo   ["-r" nil "Use remote repo instead of the local one"]})

(defn- jar-file [{:keys [artifact-id version]}]
  (path "target" (str artifact-id "-" version ".jar")))
