(ns trident.util
  "Utility library. Docstrings are omitted for simple functions; read the source
  to see what they do."
  (:require [clojure.walk :refer [postwalk]]
            [clojure.pprint]
            [clojure.spec.alpha :as s]
            [clojure.string :as str]
 #?@(:cljs [[goog.string :as gstring]
            [goog.string.format]
            [cljs.core.async :refer [<! put! chan close!]]]
      :clj [[clojure.reflect :refer [reflect]]
            [clojure.java.io :as io]]))
  #?(:cljs (:require-macros trident.util)))

(def pprint clojure.pprint/pprint)

(defn pred-> [x f g]
  (if (f x) (g x) x))

(defn derive-config
  "Replaces any `^:derived` values in `m`.

  Example:
  ```
  (def m
    {:first-name \"John\"
     :last-name  \"Doe\"
     :full-name ^:derived #(str (get-config % :first-name) \" \"
                                (get-config % :last-name))})

  (derive-config m)
  => {:first-name \"John\" :last-name  \"Doe\" :full-name \"John Doe\"}
  ```
  Any values with a truthy `:derived` metadata value must be single-argument
  functions. These functions will be replaced with their return values, with
  the config map as the argument.

  `get-config` is like `get`, but if the return value is a `:derived` function,
  `get-config` will derive it before returning. It should be used within
  `:derived` function as in the example "
  [m]
  (postwalk #(if (:derived (meta %)) (% m) %) m))

(defn get-in-config
  "See [derive-config]."
  ([m ks not-found]
   (let [x (get-in m ks not-found)]
     (if (:derived (meta x))
       (x m)
       x)))
  ([m ks]
   (get-in-config m ks nil)))

(defn get-config
  "See [derive-config]."
  ([m k not-found]
   (get-in-config m [k] not-found))
  ([m k]
   (get-config m k nil)))

(defn ^:no-doc text-rows [lines]
  (str/join \newline (remove nil? (flatten lines))))

#?(:clj (do

(defn maybe-slurp
  "Attempts to slurp `f`, returning nil on failure"
  [f]
  (try
    (slurp f)
    (catch Exception e nil)))

(defn read-config [filename]
  (some-> filename io/resource maybe-slurp read-string))

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
  `(text-rows [~@(for [[condition lines] (partition 2 forms)]
                   `(when ~condition ~lines))]))

(def ^:no-doc instant-type java.util.Date)

(def ord int)

(defn parse-int [s]
  (Long/parseLong s))

) :cljs (do

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

(def ^:no-doc instant-type (type #inst "0001-01-01"))

(def char->int (into {} (map #(vector (char %) %) (range 256))))

(def ord char->int)

(defn parse-int [s]
  (js/parseInt s))

))

(defn instant? [x]
  (= (type x) instant-type))

(defn rand-str [len]
  (apply str (take len (repeatedly #(char (+ (rand 26) 65))))))

(defn map-kv [f m]
  (into {} (map (fn [[k v]] (f k v)) m)))

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

(defn pad [n _val coll]
  (take n (concat coll (repeat _val))))

(defn format-columns
  "Formats rows of text into columns.

  Example:
  ```
  (doseq [row (format-columns [[\"hellooooooooo \" \"there\"]
                               [\"foo \" \"bar\"]
                               [\"one column\"]])]
    (println row))
  hellooooooooo there
  foo           bar
  one column
  ```"
  [rows]
  (let [n-cols (apply max (map count rows))
        rows (map (partial pad n-cols " ") rows)
        lens (apply map (fn [& column-parts]
                          (apply max (map count column-parts)))
                    rows)
        fmt (str/join (map #(str "%" (when (not (zero? %)) (str "-" %)) "s") lens))]
    (->> rows
         (map #(apply (partial format fmt) %))
         (map str/trimr))))
