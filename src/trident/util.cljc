(ns trident.util
  "Utility library. Docstrings are omitted for simple functions; read the source
  to see what they do."
  (:require [clojure.walk :refer [postwalk]]
            [clojure.pprint]
            [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [taoensso.encore :as enc]
 #?@(:cljs [[goog.string :as gstring]
            [goog.string.format]
            [cljs.core.async :refer [<! put! chan close!]]]
      :clj [[clojure.reflect :refer [reflect]]]))
  #?(:cljs (:require-macros trident.util)))

(def pprint clojure.pprint/pprint)

(defn pred-> [x f g]
  (if (f x) (g x) x))

#?(:clj (do

(defmacro capture [& xs]
  `(do ~@(for [x xs] `(def ~x ~x))))

(defmacro cljs-import-vars
  "Like `potemkin/import-vars` but supports importing cljs-only functions.

  Example:
  ```
  (cljs-import-vars my.namespace.core
                    foo bar baz)
  ```"
  [nspace & syms]
  `(do ~@(for [s syms]
           `(def ~s ~(symbol (str nspace) (str s))))))

(defmacro forv [& body]
  `(vec (for ~@body)))

(defmacro condas->
  "Combination of `cond->` and `as->`."
  [expr name & clauses]
  (assert (even? (count clauses)))
  `(as-> ~expr ~name
     ~@(map (fn [[test step]] `(if ~test ~step ~name))
            (partition 2 clauses))))

(defmacro js<!
  "Like `<!` but for js promises. See [[to-chan]]."
  [form]
  `(cljs.core.async/<! (to-chan ~form)))

(defmacro for-every? [& forms]
  `(every? boolean (for ~@forms)))

(defmacro for-some? [& forms]
  `(some boolean (for ~@forms)))

(defmacro forcat [& forms]
  `(apply concat (for ~@forms)))

(defn load-var
  "Dynamically loads a var.

  `sym`: a fully-qualified var symbol."
  [sym]
  (require (symbol (namespace sym)))
  (let [f (if-let [f (resolve sym)]
            f
            (throw (ex-info sym " is not on the classpath.")))]
    @f))

(defn loadf
  "Returns a function that dynamically loads and calls the specified function.

  `sym`: a fully-qualified function symbol."
  [sym]
  (fn [& args]
    (apply (load-var sym) args)))

(defmacro load-fns
  "Defines a bunch of \"dynamic functions\" (see [[loadf]]).

  Example:
  ```
  (load-fns
    foo my.lib/foo
    bar my.lib/bar
    baz your.lib/baz)
  (foo) ; same as (do (require 'my.lib) (my.lib/foo))
  ```"
  [& forms]
  (assert (even? (count forms)))
  `(do ~@(for [[sym fn-sym] (partition 2 forms)]
           `(def ~sym (#'loadf (quote ~fn-sym))))))

(s/def ::overrides (s/+ (s/cat :interface symbol? :fns (s/* list?))))

(defn- sigs [interface]
  (if (class? interface)
    (->> interface
         reflect
         :members
         (filter #(contains? % :parameter-types))
         (mapv (juxt :name (comp inc count :parameter-types))))
    (for [{-name :name arglists :arglists} (vals (:sigs @interface))
          arglist arglists]
      [-name (count arglist)])))

(defmacro inherit
  "Like `deftype` but with default function implementations.

  The first field is the \"parent\": for any protocol functions you don't define,
  the function will be called again with the parent as the first argument instead
  of the current object.

  Example:
  ```
  (defprotocol P
    (foo [this])
    (bar [this]))

  (deftype Parent []
    P
    (foo [_] \"parent foo\")
    (bar [_] \"parent bar\"))

  (inherit Child [parent]
    P
    (foo [_] \"child foo\"))

  (def parent (Parent.))
  (def child (Child. parent))

  (foo child)
  => \"child foo\"
  (bar child)
  => \"parent bar\"
  ```"
  [child-name [parent-instance :as fields] & overrides]
  (s/assert ::overrides overrides)
  `(deftype ~child-name ~fields
     ~@(forcat [{:keys [interface fns]} (s/conform ::overrides overrides)]
         (let [interface' (resolve interface)
               override-fns (->> fns
                                 (map (fn [form] [[(first form)
                                                   (count (second form))] form]))
                                 (into {}))]
           `[~interface
             ~@(for [[-name argcount :as sig] (sigs interface')]
                 (or (override-fns sig)
                     (let [arglist (vec (conj (repeatedly (dec argcount) gensym) '_))
                           parent-method (if (class? interface')
                                           (symbol (str "." (name -name)))
                                           (symbol (namespace interface) (name -name)))]
                       `(~-name
                          ~arglist
                          (~parent-method
                            ~parent-instance
                            ~@(rest arglist))))))]))))

(defn derive-config
  "Replaces any `^:derived` values in `m`. See [[defconfig]]."
  [m]
  (postwalk #(if (:derived (meta %)) (% m) %) m))

(defmacro defconfig
  "Defines `config` and `init-config!` vars.

  Example:
  ```
  (defconfig
    {:first-name \"John\"
     :last-name  \"Doe\"
     :full-name ^:derived #(str (:first-name %) \" \" (:last-name %))})

  config
  => {:first-name \"John\" :last-name  \"Doe\" :full-name \"John Doe\"}

  (init-config! {:first-name \"Jane\"})
  config
  => {:first-name \"Jane\" :last-name  \"Doe\" :full-name \"Jane Doe\"}
  ```
  `default` is a map. Any values with a truthy `:derived` metadata value must
  be single-argument functions. These functions will be replaced with their return
  values, with the config map as the argument.

  `init-config!` takes any number of maps and merges them onto the provided config
  map using `taoensso.encore/nested-merge`, deriving values afterward."
  [default]
  `(do
     (def ~'config (derive-config ~default))
     (defn ~'init-config! [& ms#]
       (let [c# (apply enc/nested-merge ~default ms#)
             c# (derive-config c#)]
         (def ~'config c#)))))

(defn ^:no-doc text* [lines]
  (str/join \newline (remove nil? (flatten lines))))

(defmacro text
  "Generates a string from pairs of conditions and lines of text.

  The right-hand side values will be flattened, so you can give strings,
  collections of strings, or nested collections of strings. `nil`s are
  removed.

  Example:
  ```
  (println (text
             true  [\"foo\" nil \"bar\"]
             false \"baz\"
             true  \"quux\"))
  foo
  bar
  quux
  ```"
  [& forms]
  `(text* [~@(for [[condition lines] (partition 2 forms)]
               `(when ~condition ~lines))]))

(defn text-columns
  "Formats rows of text into columns.

  Example:
  ```
  (doseq [row (text-columns [[\"hellooooooooo \" \"there\"]
                             [\"foo \" \"bar\"]])]
    (println row))
  hellooooooooo there
  foo           bar
  ```"
  [rows]
  (let [lens (apply map (fn [& column-parts]
                          (apply max (map count column-parts)))
                    rows)
        fmt (str/join (map #(str "%" (str "-" %) "s") lens))]
    (map #(apply (partial format fmt) %) rows)))

))

#?(:cljs (do

(defn to-chan
  "Converts a js promise to a channel.
  If the promise throws an error, logs to the console and closes the channel."
  [p]
  (let [c (chan)]
    (.. p (then #(put! c %))
        (catch #(do
                  (.error js/console %)
                  (close! c))))
    c))

(def ^{:doc "alias for `goog.string.format`"}
  format gstring/format)

))

(def ^:no-doc instant-type #?(:cljs (type #inst "0001-01-01")
                              :clj java.util.Date))
(defn instant? [x]
  (= (type x) instant-type))

#?(:cljs
(def char->int (into {} (map #(vector (char %) %) (range 256))))
)

(defn ord [c]
  (#?(:clj int :cljs char->int) c))

(defn parse-int [s]
  (#?(:clj Long/parseLong :cljs js/parseInt) s))

(defn rand-str [len]
  (apply str (take len (repeatedly #(char (+ (rand 26) 65))))))

(defn map-kv [f xs]
  (into {} (map #(apply f %) xs)))

(defn doclines
  "Returns the docstring of a var as a collection of lines, removing indentation."
  [_var]
  (when-some [_doc (:doc (meta _var))]
    (let [lines (str/split _doc #"\n")
          indent (->> (rest lines)
                      (map #(count (re-find #"^ *" %)))
                      (apply min ##Inf))]
      (map-indexed #(cond-> %2 (not (zero? %1)) (subs indent))
                   lines))))
