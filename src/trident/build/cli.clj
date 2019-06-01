(ns trident.build.cli
  "Tools for wrapping build tasks (commands) in CLI interfaces.

  This is basically a higher-level wrapper over `clojure.tools.cli`. Most users
  will need only [[dispatch]] and [[defcmds]]. See `trident.build` for example
  usage."
  (:require [trident.util :as u]
            [clojure.tools.cli :refer [parse-opts]]
            [clojure.string :as str]
            [clojure.pprint :refer [pprint]]
            [trident.build.util :refer [maybe-slurp with-no-shutdown]]))

(defn usage [{:keys [summary subcommands desc args-desc config]}]
  "Returns a usage string. `summary` is returned from
  `clojure.tools.cli/parse-opts`. The other keys are described in [[dispatch]]."
  (let [subcommand-len (apply max 0 (map (comp count name) (keys subcommands)))]
    (u/text
      true        [(str "Usage: <program> "
                        (when summary "[options] ")
                        (if subcommands
                          "<subcommand> [<args>]"
                          args-desc))
                   ""]
      desc        [desc ""]
      summary     ["Options:" summary ""]
      config      [(str "Config files: " (str/join "," config)) ""]
      subcommands ["Subcommands:"
                   (u/text-columns
                     (for [[cmd-name {:keys [desc]}] subcommands]
                       ["  " cmd-name "  " (u/pred-> desc (comp not string?) first)]))
                   ""
                   (str "See `<program> <subcommand> --help` to read about a specific subcommand.")])))

(defn error-msg [errors]
  "Returns a message given the errors from `clojure.tools.cli/parse-opts`."
  (str "The following errors occurred while parsing your command:\n\n"
       (str/join \newline errors)))

(defn validate-args
  "Parses `args` using `clojure.tools.cli`. Returns a map that includes
  either `:exit-code` and `:exit-msg` OR `:opts` and `:args`.

  Recognized keys include:

  | key           | description |
  | --------------|-------------|
  | `args`        | A collection of CLI options and arguments
  | `cli-options` | Parsing options for `clojure.tools.cli/parse-opts`
  | `config`      | A collection of filenames containing EDN. The contents of these files will override any defaults set in `cli-options`. (default `nil`).
  | `subcommands` | A map of commands (as described in [[dispatch]]) supported by the current command (default `nil`)

  `opt` will also be passed to [[usage]].

  `[\"-h\" \"--help\"]` are added to `cli-options`. If `config` is provided,
  then a `--config EDN` option are also added. This is similar to the
  `clj -Sdeps EDN` option."
  [{:keys [args cli-options config subcommands] :as opts}]
  ; TODO update this, rename stuff to dispatch!
  (let [subcommands? (boolean subcommands)
        cli-options (cond-> (vec cli-options)
                      config (conj [nil "--config EDN"
                                    (str "Config data to use as the last "
                                         "config file. Overrides CLI options.")])
                      true (conj ["-h" "--help"]))
        {:keys [options arguments errors summary]} (parse-opts args cli-options :in-order subcommands?)
        options (apply merge options
                       (concat
                         (->> config
                              (map #(some-> % maybe-slurp read-string))
                              (remove nil?))
                         [(:options (parse-opts args cli-options :in-order subcommands? :no-defaults true))]))
        options (merge options (when config (:config options)))
        usage (usage (assoc opts :summary summary))]
    (cond
      (:help options)
      {:exit-code 0 :exit-msg usage}

      errors
      {:exit-code 1 :exit-msg (error-msg errors)}

      :else
      {:opts options :args arguments})))

(defn- dispatch-subcommand
  ([args subcommands wrapper]
   (let [[cmd & args] args
         cli (subcommands cmd)]
     (if (some? cli)
       (wrapper #(dispatch args cli))
       (do
         (println "Command not recognized:" cmd)
         1))))
  ([args subcommands]
   (dispatch-subcommand args subcommands apply)))

(defn- exit [x]
  (if (integer? x) x 0))

(defn dispatch
  "Calls the function specified by `cmd`, `args` and `commands`.

  Returns an integer exit code. If the dispatched function returns non-nil, its
  return value will be the exit code.

  Example:

  ```
  (ns hello.core
  (:require [trident.build.cli :refer [dispatch defcmds]]
  [clojure.string :as str]))

  (defn hello [{:keys [capitalize]} the-name]
  (println \"Hello,\" (cond-> the-name capitalize str/capitalize)))

  (def cli-options
  {:capitalize [[\"-c\" nil \"Capitalize the name\"]]})

  (defcmds commands cli-options
  {\"hello\" {:fn hello :cli-options [:capitalize]}})

  (defn -main [& args]
  (System/exit (dispatch args commands)))

  ; clj -m hello.core hello -c alice
  ; -> \"Hello, Alice\"
  ```

  `cli-options` are defined as described in `clojure.tools.cli/parse-opts`
  except that the long option is defined as a key in the map, not as the second
  argument in the vector. Instead of writing:
  ```
  (def cli-options [[\"-f\" \"--foo FOO\" \"The foo\"]])
  ```
  You would write:
  ```
  (def cli-options {:foo [\"-f\" \"FOO\" \"The foo\"]})
  ```

  `commands` is a map from commands (strings) to config maps. `cmd` should be a
  key in `commands`. The following keys are recognized:

  | key              |  description
  |------------------|--------------|
  | `:fn` (required) | The function that should be called if this command is invoked.
  | `:desc`          | A description of this command. Can be either a string or a collection of strings, in which case the elements will be joined with newlines.
  | `:args-desc`     | A short argument specification, e.g. `\"[<args>]\".
  | `:cli-options`   | A collection of keys of `cli-options`, defining which options apply to this command.
  | `:append`        | A map of `cli-options` keys to strings, e.g. `{:my-option \" (This will be appended to :my-option's description)\"}`.
  | `:config`        | A collection of filenames containing EDN. The contents of these files will override any defaults set in `cli-options`.
  | `:subcommands`   | A map of commands (as described in [[dispatch]]) supported by the current command."
  [args cli]
  (exit
    (if (fn? cli)
      (with-no-shutdown
        (apply cli args))
      (let [{:keys [wrap subcommands] f :fn} cli]
        (if (= nil f wrap)
          (dispatch-subcommand args subcommands)
          (let [{:keys [opts args exit-code exit-msg]
                 :or {exit-code 0}}
                (validate-args (assoc cli :args args))]
            (if exit-msg
              (do
                (println exit-msg)
                exit-code)
              (with-no-shutdown
                (if (some? f)
                  (apply f opts args)
                  (dispatch-subcommand args subcommands #(wrap opts %)))))))))))

(defn- reduce-options [{option-keys :cli-options :as cli} options]
  (let [options (u/map-kv (fn [k v] [k (update v 1 #(str "--" (name k) " " %))])
                          (select-keys options option-keys))
        options (if-not (contains? cli :append)
                  options
                  (reduce-kv
                    (fn [options opt addendum]
                      (update-in options [opt 2] #(str % addendum)))
                    options
                    (:append cli)))]
    (update cli :cli-options #(u/pred-> % keyword? options))))

(defn reduce-cli
  ([cli options]
   (if-not (map? cli)
     cli
     (cond-> cli
       (contains? cli :cli-options) (reduce-options options)

       (contains? cli :subcommands)
       (update :subcommands (partial u/map-kv #(vector %1 (reduce-cli %2 options)))))))
  ([cli] cli))

; auto add docstring to fn based on cli? auto spec stuff (or derive stuff from spec)?
(defmacro defcli [& args]
  `(do
     (def ~'cli (apply reduce-cli ~args))
     (defn ~'-main [& args#]
       (dispatch args# ~'cli))))
