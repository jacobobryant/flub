(ns trident.cli
  "Tools for wrapping build tasks in CLIs.

  Like `cli-matic`, this provides a higher-level wrapper over
  `clojure.tools.cli`. However, `trident.cli` is designed specifically for
  making build tasks easily reusable (including tasks not defined using
  `trident.cli`).

  Most of the time you will need only [[make-cli]]. See the [[trident.build]]
  source for some non-contrived example usage."
  (:require [trident.util :as u]
            [clojure.tools.cli :refer [parse-opts]]
            [clojure.string :as str]
            [clojure.pprint :refer [pprint]]
            [trident.cli.util :refer [maybe-slurp with-no-shutdown]]))

(defn usage
  "Returns a usage string.

  `summary` is returned from `clojure.tools.cli/parse-opts`. See [[expand-cli]]
  for the other keys."
  [{:keys [summary subcommands desc args-spec config prog] :as cli}]
  (let [subcommand-len (apply max 0 (map (comp count name) (keys subcommands)))]
    (u/text
      true        (str "Usage: " (or prog "<program>") " "
                       (when summary "[options] ")
                       (if subcommands
                         "<subcommand> [<args>]"
                         args-spec))
      true        (some->> (:desc cli) (vector ""))
      summary     ["" "Options:" summary]
      config      ["" (str "Config files: " (str/join "," config))]
      subcommands [""
                   "Subcommands:"
                   (u/text-columns
                     (for [[cmd-name cli] subcommands]
                       ["  " cmd-name "  " (or (first (:desc cli)) "")]))
                   ""
                   (str "See `<program> <subcommand> --help` to read about a specific subcommand.")])))

(defn ^:no-doc error-msg [errors]
  (str/join \newline
            (concat ["The following errors occurred while parsing your command:" ""] errors)))

(defn validate-args
  "Parses `args` using `clojure.tools.cli/parse-opts`.

  Returns a map that includes either `:opts` and `:args` OR `:code` and
  `:exit-msg`.

  See [[expand-cli]] for the format of `cli`."
  [{:keys [cli-options config subcommands] :as cli} args]
  (let [subcommands? (boolean subcommands)

        {:keys [options arguments errors summary]}
        (parse-opts args cli-options :in-order subcommands?)

        {explicit-options :options}
        (parse-opts args cli-options :in-order subcommands? :no-defaults true)

        config-options (->> config
                            (map #(some-> % maybe-slurp read-string))
                            (remove nil?))
        options (->> [[options] config-options [explicit-options (:edn options)]]
                     (apply concat)
                     (apply merge))
        usage (usage (assoc cli :summary summary))]
    (cond
      (:help options)
      {:code 0 :exit-msg usage}

      errors
      {:code 1 :exit-msg (error-msg errors)}

      :else
      {:opts options :args arguments})))

(defn ^:no-doc exit-code [x]
  (if (integer? x) x 0))

(declare dispatch)

(defn ^:no-doc dispatch*
  [{:keys [subcommands] f :fn} {:keys [opts args] :as params}]
  (if (some? f)
    (let [f (cond-> f (contains? params :opts) (partial opts))]
      (with-no-shutdown (apply f args)))
    (let [[cmd & args] args
          cli (get subcommands cmd)]
      (if (some? cli)
        (dispatch cli args)
        (do (println "Subcommand not recognized:" cmd) 1)))))

(defn dispatch
  "Parses `args` and calls the function specified by `cli`.

  If `cli` contains `:fn` but not `:cli-options`, `dispatch` will pass `args`
  to `:fn` without parsing them first. See [[expand-cli]] for complete
  information about the format of `cli`.

  Returns an integer exit code. If the dispatched function returns an integer,
  that will be the exit code, otherwise it will be 0. If `System/exit` is called
  during execution, `dispatch` will disable the call and return the exit code.
  Calls to `shutdown-agents` will also be disabled."
  [cli args]
  (exit-code
    (let [{:keys [subcommands] f :fn} cli]
      (if (some #(contains? cli %) [:cli-options :config])
        (let [{:keys [code exit-msg] :as params} (validate-args cli args)]
          (if exit-msg
            (do (println exit-msg) code)
            (dispatch* cli params)))
        (dispatch* cli {:args args})))))

(defn ^:no-doc expand-options [{option-keys :cli-options :as cli} options]
  (let [options (u/map-kv (fn [k v] [k (update v 1 #(str "--" (name k) (some->> % (str " "))))])
                          (select-keys options option-keys))]
    (update cli :cli-options (fn [x] (map #(u/pred-> % keyword? options) x)))))

(def ^:no-doc edn-opt
  [nil "--edn EDN" "Additional options. Overrides CLI options."])

(def ^:no-doc help-opt ["-h" "--help"])

(defn ^:no-doc add-opt [cli opt]
  (update cli :cli-options #(conj (vec %) opt)))

(defn expand-cli
  "Returns an expanded form of `compact-cli`, suitable for [[dispatch]].

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

  `compact-cli` is a map that can have the following keys:

   - `:fn`: a function or function var. If present, [[dispatch]] will apply this
     function to the parsed options and any remaining arguments, as returned by
     `clojure.tools.cli/parse-opts`. If not present, `:subcommands` must be
     present.

   -  `:desc`: a seq of strings describing this task, used in the `--help`
     documentation output. If `:desc` is omitted and `:fn` is a var, this will
     be derived from the function's docstring.

   - `:cli-options`: a seq of keys in `options`. This will be replaced with a
     value in the format specified by `parse-opts`. If `:cli-options` is
     present, `--help` and `--edn` options will also be added. `--edn` is
     similar to the `clj -Sdeps <EDN>` option.

   - `:config`: a seq of filenames. If any of the files exist, their contents
     will be read as EDN and merged (in the order given) with the results of
     `parse-opts`. Config files will override default option values but will
     be overridden by any explicitly provided CLI options. Config files can
     contain keys not included in the CLI options.

   - `:subcommands`: a map from strings to more `compact-cli` maps. If `:fn` is
     omitted, [[dispatch]] will treat the first non-option argument as a key in
     `:subcommands` and continue dispatching recursively. [[expand-cli]] will
     also recursively expand the values of `:subcommands`.

   - `:prog`: text to use for the program name in the \"Usage: ...\" line in
     `--help` output, e.g. `\"clj -m my.namespace\"`.

   - `:args-spec`: a specification of the non-option arguments to use in the
     \"Usage: ...\" line in `--help` output, e.g. `\"[foo1 [foo2 ...]]\"`.

  **Reusing build tasks**

  For convenience, `compact-cli` can be a function or function var instead of a
  map. In this case, it will be replaced with `{:fn <fn>}`. This can be
  useful for curating build tasks as subcommands, especially build tasks not
  defined with `trident.cli`. For example:
  ```
  (expand-cli {:subcommands {\"pom\" #'some.ns.pom/-main
                             \"jar\" #'some.ns.jar/-main}})
  ```
  Since the subcommands are vars, the `--help` option output will include the
  first line of their docstrings. (If the functions don't have docstrings,
  you can always use the map form for `compact-cli` and include `:desc`
  yourself).

  Since `make-cli` returns the expanded `cli` map, you can reuse it:
  ```
  (expand-cli
    {:subcommands
      (merge
        ; Regular build tasks, not defined with trident.build
        {\"pom\" #'some.ns.pom/-main
         \"jar\" #'some.ns.jar/-main}
        (:subcommands some.ns.deploy/cli))})) ; `cli` defined with `make-cli`
  ```
  `expand-cli` is idempotent, so it's safe to give it CLI maps that have already
  been expanded."
  ([compact-cli options]
   (if (::expanded (meta compact-cli))
     compact-cli
     (u/condas-> compact-cli x
       (not (map? x))                  {:fn x}
       (and (var? (:fn x))
            (not (contains? x :desc))) (assoc x :desc (u/doclines (:fn x)))
       (var? (:fn x))                  (update x :fn deref)
       (contains? x :cli-options)      (expand-options x options)
       (some #(contains? x %)
             [:cli-options :config])   (add-opt x edn-opt)
       (or (contains? x :cli-options)
           (not (contains? x :fn)))    (add-opt x help-opt)
       (contains? x :subcommands)      (->> #(vector %1 (expand-cli %2 options))
                                            (partial u/map-kv)
                                            (update x :subcommands))
       true                            (with-meta x {::expanded true}))))
  ([compact-cli] (expand-cli compact-cli {})))

(defn main-fn
  "Returns a function suitable for binding to `-main`. See [[make-cli]]."
  [cli]
  (fn [& args] (System/exit (dispatch cli args))))

(defn make-cli
  "Returns a map with the keys `:cli`, `:main-fn` and `:help`.

  `cli`: an expanded form of `compact-cli` and `options`, suitable for passing to
  [[dispatch]]. See [[expand-cli]] for the format of `compact-cli` and `options`,
  including tips about how to reuse other build tasks.

  `main-fn`: a function suitable for binding to `-main`. It will call
  [[dispatch]], afterwards calling `System/exit` with the function's return
  value (if it's an integer) as the exit code.

  `help`: the auto-generated `--help` output for this task. Good for including in
  `-main`'s docstring.

  Example:
  ```
  (defn hello
    \"Give a friendly greeting.\"
    [{:keys [capitalize]} the-name]
    (println \"Hello,\" (cond-> the-name capitalize clojure.string/capitalize)))

  (def compact-cli {:fn #'hello
                    :cli-options [:capitalize]})

  (def options {:capitalize [\"-c\" nil \"Capitalize the name\"]})

  (let [{:keys [cli main-fn help]} (make-cli compact-cli options)]
    (def ^{:doc help} -main main-fn)
    ; `cli` is exposed so it can be reused if needed.
    (def cli cli))

  ; Normally `main-fn` will shutdown the JVM, but we can prevent this using
  ; `trident.cli.util/with-no-shutdown`:
  => (with-no-shutdown (-main \"--help\"))
  Usage: <program> [options]

  Give a friendly greeting.

  Options:
    -c, --capitalize   Capitalize the name
    -h, --help
  0 ; 0 is the return value/exit code.

  => (with-no-shutdown (-main \"--capitalize\" \"alice\"))
  Hello, Alice
  0
  ```"
  ([compact-cli options]
   (let [cli (expand-cli compact-cli options)
         help (when (some #(= (get % 1) "--help") (:cli-options cli))
                (str "```\n"
                     (with-out-str (dispatch cli ["--help"]))
                     "```"))]
     {:cli cli
      :main-fn (main-fn cli)
      :help help}))
  ([compact-cli] (make-cli compact-cli {})))
