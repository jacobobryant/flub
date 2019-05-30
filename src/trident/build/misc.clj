(ns trident.build.misc
  "Miscellaneous build tasks.

  See `trident.build` for usage."
  (:require [trident.build.util :refer [sh abspath with-dir]]
            [trident.build.pom :as pom]
            [mach.pack.alpha.skinny :as skinny]
            [deps-deploy.deps-deploy :as deps-deploy]))

(defn- jar-file [{:keys [artifact-id version]}]
  (abspath "target" (str artifact-id "-" version ".jar")))

(defn jar [opts]
  ; TODO read artifact-id, version from pom
  (let [jar-file (jar-file opts)]
    ; TODO use raynes?
    (sh "rm" "-f" jar-file)
    (sh "mkdir" "-p" "target/extra/META-INF/")
    (sh "cp" "pom.xml" "target/extra/META-INF")
    (skinny/-main "--no-libs" "-e" (abspath "target/extra") "--project-path" jar-file))) ; ?

(defn- lib-task [command {:keys [skip-jar] :as opts}]
  (assert (contains? #{"install" "deploy"} command))
  (when (not skip-jar)
    (println "generating pom")
    (pom/sync-pom opts)
    (println "packaging")
    (jar opts)
    (println (str command "ing")))
  (deps-deploy/-main command (jar-file opts)))
(def install (partial lib-task "install"))
(def deploy (partial lib-task "deploy"))

; untested from clj
;(defn iondeploy [uname]
;  (let [push-out (sh "clojure" "-Adev" "-m" "datomic.ion.dev"
;                     (str {:op :push :uname uname}))
;        _ (print push-out)
;        deploy-cmd (get-in (read-string (str "[" push-out "]")) [1 :deploy-command])
;        deploy-out (sh "bash" "-c" deploy-cmd)
;        _ (print deploy-out)
;        status-cmd (:status-command (read-string deploy-out))]
;    ;todo auto quit
;    (while true
;      (print (sh "bash" "-c" status-cmd))
;      (sh "sleep" "5"))))

(defn cljdoc [{:keys [group-id artifact-id version cljdoc-dir git-dir remote-repo]}]
  (let [git-dir (abspath git-dir)
        args (cond-> ["./script/cljdoc" "ingest" "-p" (str group-id "/" artifact-id)
                      "-v" version]
               (not remote-repo) (concat ["--git" git-dir]))]
    (with-dir cljdoc-dir
      (print (apply sh args)))))
