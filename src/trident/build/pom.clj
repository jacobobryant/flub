(ns trident.build.pom
  (:require [trident.build.xml :refer [xml-replace]]
            [trident.build.util :refer [abspath sh]]
            [clojure.data.xml :as xml]
            [clojure.tools.deps.alpha.gen.pom :as gen.pom]
            [clojure.java.io :as io]
            [clojure.tools.deps.alpha.reader :refer [read-deps]]
            [clojure.string :refer [ends-with?]]
            [clojure.zip :as zip]))

(xml/alias-uri 'pom "http://maven.apache.org/POM/4.0.0")

(defn sync-pom [{:keys [group-id artifact-id version github-repo]}]
  (let [clean? (= "" (sh "git" "status" "--porcelain"))
        _ (when (not (or clean? (ends-with? version "SNAPSHOT")))
            (throw (ex-info "Can't release without a clean commit" {})))
        commit (sh "git" "rev-list" "-n" "1" "HEAD")
        pom-path (abspath "pom.xml")]
    (io/delete-file pom-path true)
    (gen.pom/sync-pom
      (read-deps [(io/file (abspath "deps.edn"))])
      (io/file (abspath ".")))
    (-> pom-path io/file io/input-stream xml/parse
        (xml-replace
          ::pom/groupId group-id
          ::pom/artifactId artifact-id
          ::pom/version version)
        zip/xml-zip
        (zip/insert-child
          (xml/sexp-as-element
            [::pom/scm
             [::pom/connection (str "scm:git:git://github.com/" github-repo ".git")]
             [::pom/developerConnection (str "scm:git:ssh://git@github.com/" github-repo ".git")]
             [::pom/tag commit]
             [::pom/url (str "https://github.com/" github-repo)]]))
        zip/root
        xml/indent-str
        (->> (spit pom-path)))))
