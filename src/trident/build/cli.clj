(ns trident.build.cli
  "Tools for creating modular build tasks (commands). See
  [trident.build](https://github.com/jacobobryant/trident/blob/master/src/trident/build.clj)
  for example usage.

  Commands are configured in a data structure as described in [[dispatch]].
  Commands can be easily reused like so:

  ```
  (ns alice.build)

  (def commands
    {\"foo\" ...
     \"bar\" ...})

  (ns bob.build)

  (def commands
    {\"baz\" ...
     \"quux\" ...})

  (ns carol.build
    (require [alice.build :as alice]
             [bob.build :as bob]))

  (def commands
    (merge alice/commands
           (select-keys bob/commands [\"baz\"])
           {\"quux\" ...}))
  ```"
  (:require [clojure.tools.cli :refer [parse-opts]]
            [clojure.string :as str]
            [clojure.pprint :refer [pprint]]
            [trident.build.util :refer [maybe-slurp]]))

(defn usage [summary subcommands]
  (let [lines ["Usage: <program-name> [options] [<args>]"
               ""
               "Options:"
               summary]
        lines (cond-> lines
                subcommands (concat ["" (str "Subcommands: " (str/join ", " subcommands))]))]
    (str/join \newline lines)))

(defn error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (str/join \newline errors)))

(defn validate-args
  "Parses `args` using `clojure.tools.cli`. Returns a map that includes
  either `:exit-code` and `:exit-msg` OR `:opts` and `:args`.

  | key           | description |
  | --------------|-------------|
  | `args`        | A collection of CLI options and arguments
  | `cli-options` | Parsing options for `clojure.tools.cli/parse-opts`
  | `defaults`    | A list of filenames containing EDN. The contents of these files will override any defaults set in `cli-options`. (default `nil`).
  | `subcommands` | A list of subcommands supported by the current command (default `nil`)
  | `validate-fn` | A fn of two arguments, a map of the parsed options and a collection of any remaining arguments, which returns `true` or `false` (default `(constantly true)`)

  `[\"-h\" \"--help\"]` are added to `cli-options`. If `defaults` is
  provided, then a `--config EDN` option are also added. This is similar to
  the `clj -Sdeps EDN` option."
  [{:keys [args cli-options defaults subcommands validate-fn]
    :or {validate-fn (constantly true) }}]
  (let [in-order (boolean subcommands)
        cli-options (cond-> cli-options
                      defaults (conj [nil "--config EDN"
                                      (str "Config data to use as the last "
                                           "config file. Overrides cli options.")])
                      true (conj ["-h" "--help"]))
        {:keys [options arguments errors summary]} (parse-opts args cli-options :in-order in-order)
        options (apply merge options
                       (concat
                         (->> defaults
                              (map #(some-> % maybe-slurp read-string))
                              (remove nil?))
                         [(:options (parse-opts args cli-options :in-order in-order :no-defaults true))]))
        options (merge options (when defaults (:config options)))]
    (cond
      (:help options)
      {:exit-code 0 :exit-msg (usage summary subcommands)}

      errors
      {:exit-code 1 :exit-msg (error-msg errors)}

      (validate-fn options arguments)
      {:opts options :args arguments}

      :else
      {:exit-code 1 :exit-msg (usage summary subcommands)})))

(defn dispatch
  "Parses `args` using [[validate-args]] and passes them to the function
  specified by `cmd` and `commands`. Returns an integer exit code.

  ```
  (ns hello.core
    (:require [trident.build.cli :refer [dispatch]]
              [clojure.string :as str]))

  (defn hello [{:keys [capitalize]} the-name]
    (println \"Hello,\" (cond-> the-name capitalize str/capitalize)))

  (def commands
    {\"hello\" {:fn hello
              :cli-options
              [[\"-c\" \"--capitalize\" \"Capitalize the name\"]]}})

  (defn -main [cmd & args]
    (System/exit
      (dispatch {:commands commands :cmd cmd :args args})))

  ; clj -m hello.core hello -c alice
  ; -> \"Hello, Alice\"
  ```

  `commands` is a map from commands (strings) to config maps. Each config map
  includes a :fn key which specifies the function for this command. All other
  keys will be passed to [[validate-args]]. `cmd` is a key in `commands`."
  [{:keys [cmd args commands]}]
  (let [opts (commands cmd)
        {:keys [args exit-code exit-msg] fn-opts :opts
         :or {exit-code 0}}
        (validate-args (-> opts
                           (dissoc :fn)
                           (assoc :args args)))]
    (if exit-msg
      (do
        (println exit-msg)
        exit-code)
      (or (apply (:fn opts) fn-opts args) 0))))
