(ns trident.cli
  "Tools for wrapping build tasks in CLIs.

  Like `cli-matic`, this provides a higher-level wrapper over
  `clojure.tools.cli`. However, `trident.cli` is designed specifically for
  making build tasks easily reusable (including tasks not defined using
  `trident.cli`).

  See [[_cli_format]].
  Most of the time, the only thing you'll need from this namespace is
  [[defmain]]. See the [[trident.build]] source for some non-contrived example
  usage."
  (:require [trident.util :as u]
            [clojure.tools.cli :refer [parse-opts]]
            [clojure.string :as str]
            [clojure.pprint :refer [pprint]]
            [me.raynes.fs :as fs]
            [trident.cli.util :refer [maybe-slurp with-no-shutdown read-deps]]))

(defn description
  "Returns `cli`'s description as a seq of lines.

  The description will be derived from cli's fn's docstring if `:desc` isn't
  present."
  [cli]
  (cond
    (contains? cli :desc) (:desc cli)
    (var? (:fn cli))      (u/doclines (:fn cli))))

(defn usage
  "Returns a usage string.

  `summary` is returned from `clojure.tools.cli/parse-opts`."
  [{:keys [subcommands args-spec config prog] :as cli} summary]
  (u/text
    true        (str "Usage: " (or prog "<program>") " "
                     (when summary "[options] ")
                     (if subcommands
                       "<subcommand> [<args>]"
                       args-spec))
    true        (some->> cli description (vector ""))
    summary     ["" "Options:" summary]
    config      ["" (str "Config is stored under the " config " key in deps.edn.")]
    subcommands [""
                 "Subcommands:"
                 (u/format-columns
                   (for [[cmd-name cli] subcommands]
                     ["  " cmd-name "  " (or (first (description cli)) "")]))
                 ""
                 (str "See `<program> <subcommand> --help` to read about a specific subcommand.")]))

(defn validate-args
  "Parses `args` using `clojure.tools.cli/parse-opts`.

  Adds `--edn` and `--help` opts. `--edn` is similar to the `clj -Sdeps <EDN>`
  option. Returns a map that includes either `:opts` and `:args` OR `:code` and
  `:exit-msg`."
  [{:keys [options option-keys config subcommands] :as cli} args]
  (let [subcommands? (boolean subcommands)
        cli-options (for [k option-keys]
                      (update (options k) 1 #(str "--" (name k) (some->> % (str " ")))))
        cli-options (if (contains? cli :fn)
                      (concat cli-options [[nil "--edn EDN" "Additional options. Overrides CLI options."]])
                      cli-options)
        cli-options (concat cli-options [["-h" "--help"]])

        {:keys [options arguments errors summary]}
        (parse-opts args cli-options :in-order subcommands?)

        {explicit-options :options}
        (parse-opts args cli-options :in-order subcommands? :no-defaults true)

        config-options (when (and config (fs/exists? "deps.edn"))
                         (get (read-string (slurp "deps.edn")) config))
        options (merge options config-options explicit-options (:edn options))
        usage (usage cli summary)]
    (cond
      (:help options)
      {:code 0 :exit-msg usage}

      errors
      {:code 1 :exit-msg (str "The following errors ocurred while parsing your command:\n"
                              (str/join "\n" errors))}

      :else
      {:opts options :args arguments})))

(defn cli-processing?
  "Returns true if arguments should be parsed before passing them to `(:fn cli)`.

  Parsing is disabled if `:fn` is specified but `:option-keys` and `:config`
  aren't. This can be overridden by setting `:cli-processing?`."
  [cli]
  (get cli :cli-processing?
       (or (not (contains? cli :fn))
           (some #(contains? cli %) [:option-keys :config]))))

(defn dispatch
  "Parses `args` and calls the function or subcommand specified by `cli`.

  Returns an integer exit code. If the dispatched function returns an integer,
  that will be the exit code, otherwise it will be 0. If `System/exit` is called
  during execution, `dispatch` will disable the call and return the exit code.
  Calls to `shutdown-agents` will also be disabled."
  [cli args]
  (let [{:keys [subcommands] f :fn :as cli} (if (map? cli) cli {:fn cli})
        process? (cli-processing? cli)
        {:keys [code exit-msg opts args]} (if process?
                                            (validate-args cli args)
                                            {:args args})
        code (cond
               exit-msg  (do (println exit-msg) code)
               (some? f) (with-no-shutdown (apply (cond-> f process? (partial opts)) args))
               :default  (let [[cmd & args] args
                               cli (get subcommands cmd)]
                           (if (some? cli)
                             (dispatch cli args)
                             (do (println "Subcommand not recognized:" cmd) 1))))]
    (if (integer? code) code 0)))

(defn main-fn
  "Returns a function suitable for binding to `-main`.

  Calls through to [[dispatch]] and passes the return value to `System/exit`."
  [cli]
  (fn [& args] (System/exit (dispatch cli args))))

(defn help
  "Returns the `--help` output for `cli`.

  Returns nil if `cli`'s function does its own CLI processing. See
  [[cli-processing?]]."
  [cli]
  (when (cli-processing? cli)
    (str "```\n" (with-out-str (dispatch cli ["--help"])) "```")))

(defmacro defmain
  "Defines `-main` as an entry point for `cli`.

  `cli`'s `--help` output is used as the docstring. See [[_cli_format]], [[help]]
  and [[main-fn]]."
  [cli]
  `(do (def ~'-main (main-fn ~cli))
       (alter-meta! #'~'-main assoc :doc (help ~cli))))

(def
^{:doc
"Build task interfaces are defined using a `cli` object. Example:
```
(defn hello
  \"Give a friendly greeting.\"
  [{:keys [capitalize]} the-name]
  (println \"Hello,\" (cond-> the-name capitalize clojure.string/capitalize)))

(def options {:capitalize [\"-c\" nil \"Capitalize the name\"]
              :foo [\"-x\" \"FOO\" \"Stores an argument in `:foo`.\"]})

(def cli {:fn #'hello
          :prog \"clj -m hello\"
          :options options
          :option-keys [:capitalize]})

(defmain cli)

; Normally `main-fn` will shutdown the JVM, but we can prevent this using
; `trident.cli.util/with-no-shutdown`:
=> (with-no-shutdown (-main \"--help\"))
Usage: clj -m hello [options]

Give a friendly greeting.

Options:
  -c, --capitalize  Capitalize the name
      --edn EDN     Additional options. Overrides CLI options.
  -h, --help
0 ; 0 is the return value/exit code.

=> (with-no-shutdown (-main \"--capitalize\" \"alice\"))
Hello, Alice
0
```

`options` are the same as described in `clojure.tools.cli/parse-opts` except
that the long option is defined as a key in the map, not as the second
argument in the vector. Instead of writing:
```
(def options [[\"-f\" \"--foo FOO\" \"The foo\"]
              [\"-b\" \"--bar\" \"Toggle the bar\"]])
```
You would write:
```
(def options {:foo [\"-f\" \"FOO\" \"The foo\"]
              :bar [\"-b\" nil \"Toggle the bar\"})
```
Options are defined like this so that they can be easily reused by other build
tasks.

`cli` can have the following keys:

 - `:fn`: a function or function var. If present, [[dispatch]] will apply this
   function to the parsed options and any remaining arguments, as returned by
   `clojure.tools.cli/parse-opts`. If not present, `:subcommands` must be
   present.

 - `:desc`: a seq of strings describing this task, used in the `--help` output.
   If `:desc` is omitted and `:fn` is a var, this will be derived from the
   function's docstring.

 - `:options`: see above.

 - `:option-keys`: a seq of keys in the `:options` map This defines which
   options are actually used for this command. `--edn` and `--help` options
   will be added automatically unless `:fn` does its own CLI processing.
   See [[validate-args]] and [[cli-processing?]].

 - `:config`: a seq of filenames. If any of the files exist, their contents
   will be read as EDN and merged (in the order given) with the results of
   `parse-opts`. Config files will override default option values but will
   be overridden by any explicitly provided CLI options. Config files can
   contain keys not included in the CLI options.

 - `:subcommands`: a map from strings to more `cli` maps. If `:fn` is omitted,
   [[dispatch]] will treat the first argument as a key in `:subcommands`
   and continue dispatching recursively.

 - `:prog`: text to use for the program name in the \"Usage: ...\" line in
   `--help` output, e.g. `\"clj -m my.namespace\"`.

 - `:args-spec`: a specification of the non-option arguments to use in the
   \"Usage: ...\" line in `--help` output, e.g. `\"[foo1 [foo2 ...]]\"`.

 - `:cli-processing?`: see [[cli-processing?]].

See the [[trident.build]] source for some more in-depth examples.

**Reusing build tasks**

You can use build tasks that aren't defined with `trident.cli`. Example:
```
(ns some.ns.pom)

(defn -main
  \"Some pom task\"
  [& args]
  (when (= (first args) \"--help\")
    (println \"hello\"))
  (System/exit 0))

...

(ns your.ns
  (:require [trident.cli :refer [defmain]]
            [trident.cli.util :refer [with-no-shutdown]]))

; For convenience, `#'some.ns.pom/-main` is the same as
; `{:fn #'some.ns.pom/-main}`.
(def cli {:subcommands {\"pom\" #'some.ns.pom/-main
                        \"jar\" #'some.ns.jar/-main}})
(defmain cli)

=> (with-no-shutdown (-main \"--help\"))
Usage: <program> [options] <subcommand> [<args>]

Options:
  -h, --help

Subcommands:
  pom  Some pom task
  jar  Some jar task

See `<program> <subcommand> --help` to read about a specific subcommand.
0

=> (with-no-shutdown (-main \"pom\" \"--help\"))
hello
0
```"}
_cli_format)
