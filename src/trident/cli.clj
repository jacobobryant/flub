(ns trident.cli
  "Tools for wrapping build tasks (commands) in CLI interfaces.

  This is basically a higher-level wrapper over `clojure.tools.cli`. Most users
  will need only [[dispatch]] and [[defcmds]]. See `trident.build` for example
  usage."
  (:require [trident.util :as u]
            [clojure.tools.cli :refer [parse-opts]]
            [clojure.string :as str]
            [clojure.pprint :refer [pprint]]
            [trident.cli.util :refer [maybe-slurp with-no-shutdown with-dir]]))

(defn- cli-desc [{:keys [cli short?] :or {short? true}}]
  (cond
    (symbol? cli) (let [{cli' ::cli doc' :doc} (-> cli resolve meta)]
                    (cond
                      (some? cli') (cli-desc cli')
                      (some? doc') (cond-> doc' short? first)))
    (map? cli) (or (:desc cli) (cli-desc {:cli (:fn cli) :short? short?}))
    :default (if short? "" [])))

(defn usage [{:keys [summary subcommands desc args-desc config] :as cli}]
  "Returns a usage string. `summary` is returned from
  `clojure.tools.cli/parse-opts`. The other keys are described in [[dispatch]]."
  (let [subcommand-len (apply max 0 (map (comp count name) (keys subcommands)))]
    (u/text
      true        (str "Usage: <program> "
                       (when summary "[options] ")
                       (if subcommands
                         "<subcommand> [<args>]"
                         args-desc))
      true        (:desc cli)
      summary     ["" "Options:" summary]
      config      ["" (str "Config files: " (str/join "," config))]
      subcommands [""
                   "Subcommands:"
                   (u/text-columns
                     (for [[cmd-name cli] subcommands]
                       ["  " cmd-name "  " (or (first (:desc cli)) "")]))
                   ""
                   (str "See `<program> <subcommand> --help` to read about a specific subcommand.")])))

(defn error-msg [errors]
  "Returns a message given the errors from `clojure.tools.cli/parse-opts`."
  (str "The following errors occurred while parsing your command:\n\n"
       (str/join \newline errors)))

(defn validate-args
  "Parses `args` using `clojure.tools.cli`. Returns a map that includes
  either `:code` and `:exit-msg` OR `:opts` and `:args`.

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
      {:code 0 :exit-msg usage}

      errors
      {:code 1 :exit-msg (error-msg errors)}

      :else
      {:opts options :args arguments})))

(declare dispatch)

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

(defn- exit-code [x]
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
  (exit-code
    (let [{:keys [wrap subcommands] f :fn} cli]
      (if (= nil f wrap)
        (dispatch-subcommand args subcommands)
        (let [{:keys [opts args code exit-msg]}
              (validate-args (assoc cli :args args))]
          (if exit-msg
            (do
              (println exit-msg)
              code)
            (with-no-shutdown
              (if (some? f)
                (apply f opts args)
                (dispatch-subcommand args subcommands #(wrap opts %))))))))))

(defn- expand-options [{option-keys :cli-options :as cli} options]
  (let [options (u/map-kv (fn [k v] [k (update v 1 #(str "--" (name k) " " %))])
                          (select-keys options option-keys))]
    (update cli :cli-options (fn [x] (map #(u/pred-> % keyword? options) x)))))

(def ^:private config-opt
  [nil "--config EDN" "Config data to use as the last config file. Overrides CLI options."])

(defn expand-cli
  ([cli options]
   (if (::expanded (meta cli))
     cli
     (u/condas-> cli x
       (var? x)                        {:fn x}
       (and (var? (:fn x))
            (not (contains? x :desc))) (assoc x :desc (u/doclines (:fn x)))
       (var? (:fn x))                  (update x :fn deref)
       (contains? x :cli-options)      (expand-options x options)
       (contains? x :config)           (update x :cli-options #(conj (vec %) config-opt))
       (contains? x :cli-options)      (update x :cli-options #(conj (vec %) ["-h" "--help"]))
       (contains? x :subcommands)      (->> #(vector %1 (expand-cli %2 options))
                                            (partial u/map-kv)
                                            (update x :subcommands))
       true                            (with-meta x {::expanded true}))))
  ([cli] (expand-cli cli {})))

(defn main-fn [cli]
  (fn [& args] (System/exit (dispatch args cli))))

(defn make-cli [cli & args]
  (let [cli (apply expand-cli cli args)]
    {:cli cli
     :main-fn (main-fn cli)}))
