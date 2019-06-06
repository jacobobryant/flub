(ns trident.build.pom
  (:require [clojure.zip :as zip]
            [clojure.data.zip.xml :as zipx]
            [trident.cli :refer [make-cli]]
            [trident.cli.util :refer [path sh]]
            [trident.build.lib :refer [cli-options]]
            [clojure.data.xml :as xml]
            [clojure.tools.deps.alpha.gen.pom :as gen.pom]
            [clojure.java.io :as io]
            [clojure.tools.deps.alpha.reader :refer [read-deps]]
            [clojure.string :refer [ends-with? trim]]
            [clojure.zip :as zip]))

(xml/alias-uri 'pom "http://maven.apache.org/POM/4.0.0")

(defn xml-replace
  "Replaces the value of the first `k` tag with `v`"
  ([xml k v]
   (-> xml
       zip/xml-zip
       (zipx/xml1-> k)
       zip/down
       (zip/replace v)
       zip/root))
  ([xml k v & kvs]
   (let [ret (xml-replace xml k v)]
     (if kvs
       (if (next kvs)
         (recur ret (first kvs) (second kvs) (nnext kvs))
         (throw (IllegalArgumentException.
                  "xml-replace expects even number of arguments after map, found odd number")))
       ret))))

; todo handle missing options
(defn sync-pom
  "Generates a new pom.xml in the current directory (overwrites any existing pom.xml)."
  [{:keys [group-id artifact-id version github-repo]}]
  (let [clean? (= "" (sh "git" "status" "--porcelain"))
        _ (when (not (or clean? (ends-with? version "SNAPSHOT")))
            (throw (ex-info "Can't do a non-snapshot release without a clean commit" {})))
        commit (trim (sh "git" "rev-list" "-n" "1" "HEAD"))
        pom-path (path "pom.xml")]
    (io/delete-file pom-path true)
    (gen.pom/sync-pom
      (read-deps [(io/file (path "deps.edn"))])
      (io/file (path ".")))
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

(let [{:keys [cli main-fn help]}
      (make-cli {:fn #'sync-pom
                 :config ["lib.edn"]
                 :cli-options [:group-id :artifact-id :version :github-repo]}
                cli-options)]
  (def cli cli)
  (def ^{:doc help} -main main-fn))
