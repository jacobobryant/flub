(ns trident.ion-dev.deploy
  "Build tasks for ions."
  (:require [trident.cli :refer [defmain]]
            [datomic.ion.dev :as iondev]
            [clojure.pprint :refer [pprint]]
            [clojure.string :as str]))

(defn deploy
  "Deploys a Datomic Cloud Ion.

  `opts` will be passed to `datomic.ion.dev/{push,deploy}`. If `group` is
  omitted, the first deploy group will be used. This is ok for a solo topology,
  but you should provide `group` in production topologies.

  `exclude` is collection of `<group-id>/<artifact-id` symbols. If there are
  any dependency conflicts not in `exclude`, `deploy` will fail.

  Returns 0 on success, 1 on failure."
  [{:keys [exclude group] :as opts}]
  (let [push-result (iondev/push opts)
        dependency-conflicts (-> push-result
                                 (get-in [:dependency-conflicts :deps])
                                 (as-> x (apply dissoc x exclude))
                                 not-empty)]
    (pprint push-result)
    (if dependency-conflicts
      (do (println "You must fix remove dependency conflicts before continuing.") 1)
      (let [deploy-result (-> {:group (first (:deploy-groups push-result))}
                              (merge opts)
                              iondev/deploy)]
        (loop [status (iondev/deploy-status deploy-result)]
          (prn status)
          (case (:deploy-status status)
            "RUNNING" (do
                        (Thread/sleep 3000)
                        (recur (iondev/deploy-status deploy-result)))
            "SUCEEDED" 0
            1))))))

(def cli
  {:fn #'deploy
   :desc ["Deploys a Datomic Cloud Ion."]
   :options {:uname ["-u" "UNAME" "A uname for pushing"]
             :rev ["-r" "REV" "Rev for pushing"]
             :exclude ["-e" "DEPS" "A colon-separated list of dependency conflicts to ignore"
                       :parse-fn #(map symbol (str/split % #":"))]}
   :option-keys [:rev :uname :exclude]
   :config :trident/ion
   :prog "clj -m trident.ion.build"})

(defmain cli)
