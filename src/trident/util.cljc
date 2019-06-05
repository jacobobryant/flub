(ns trident.util
  (:require [clojure.walk :refer [postwalk]]
            [clojure.pprint]
            [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [taoensso.encore :as enc]
 #?@(:cljs [[goog.string :as gstring]
            [goog.string.format]
            [cljs.core.async :refer [<! put! chan close!]]]
      :clj [[potemkin :refer [import-vars]]
            [clojure.reflect :refer [reflect]]]))
  #?(:cljs (:require-macros trident.util)))

(def pprint clojure.pprint/pprint)

(defn map-from
  [f xs]
  (into {} (for [x xs] [x (f x)])))

(defn pred-> [x f g]
  (if (f x) (g x) x))

#?(:clj (do

(defmacro capture [& xs]
  `(do ~@(for [x xs] `(def ~x ~x))))

(defmacro pullall [& nses]
  `(import-vars
     ~@(for [n nses]
         (into [n] (keys (ns-publics n))))))

(defmacro cljs-pullall [nspace & syms]
  `(do ~@(for [s syms]
           `(def ~s ~(symbol (str nspace) (str s))))))

(defmacro forv [& body]
  `(vec (for ~@body)))

(defmacro condas->
  "Combination of as-> and cond->."
  [expr name & clauses]
  (assert (even? (count clauses)))
  `(as-> ~expr ~name
     ~@(map (fn [[test step]] `(if ~test ~step ~name))
            (partition 2 clauses))))

; todo move instead of copy
(defn move [src dest]
  (spit dest (slurp src)))

(defn manhattand [a b]
  (->> (map - a b)
       (map #(Math/abs %))
       (apply +)))

(defmacro js<! [form]
  `(cljs.core.async/<! (to-chan ~form)))

(defmacro for-every? [& forms]
  `(every? boolean (for ~@forms)))

(defmacro for-some? [& forms]
  `(some boolean (for ~@forms)))

(defmacro forcat [& forms]
  `(apply concat (for ~@forms)))

(defn loadx [sym]
  (require (symbol (namespace sym)))
  (let [f (if-let [f (resolve sym)]
            f
            (throw (ex-info sym " is not on the classpath.")))]
    @f))

(defn loadf [sym]
  (fn [& args]
    (apply (loadx sym) args)))

(defmacro load-fns [& forms]
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

(defmacro inherit [child-name [parent-instance :as fields] & overrides]
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

(defn derive-config [m]
  (postwalk #(if (:derived (meta %)) (% m) %) m))

(defmacro defconfig [default]
  `(do
     (def ~'config (derive-config ~default))
     (defn ~'init-config! [& ms#]
       (let [c# (apply enc/nested-merge ~default ms#)
             c# (derive-config c#)]
         (def ~'config c#)))))

(defn text* [lines]
  (str/join \newline
            (pred-> (remove nil? (flatten lines))
                    (comp empty? last) butlast)))

(defmacro text [& forms]
  `(text* [~@(for [[condition lines] (partition 2 forms)]
               `(when ~condition ~lines))]))

(defn text-columns [rows]
  (let [lens (apply map (fn [& column-parts]
                          (apply max (map count column-parts)))
                    rows)
        fmt (str/join (map #(str "%" (str "-" %) "s") lens))]
    (map #(apply (partial format fmt) %) rows)))

))

#?(:cljs (do

(defn to-chan [p]
  (let [c (chan)]
    (.. p (then #(put! c %))
        (catch #(do
                  (.error js/console %)
                  (close! c))))
    c))

(def format gstring/format)

))

(def ^:private instant-type #?(:cljs (type #inst "0001-01-01")
                               :clj java.util.Date))
(defn instant? [x]
  (= (type x) instant-type))

(defn indexed [xs]
  (map-indexed vector xs))

(defn dissoc-by [m f]
  (into {} (remove (comp f second) m)))

(defn map-inverse [m]
  (reduce
    (fn [inverse [k v]]
      (update inverse v
              #(if (nil? %)
                 #{k}
                 (conj % k))))
    {}
    m))

(defn conj-some [coll x]
  (cond-> coll
    x (conj x)))

(defn assoc-some [m k v]
  (cond-> m (some? v) (assoc k v)))

(defn split-by [f coll]
  (reduce
    #(update %1 (if (f %2) 0 1) conj %2)
    [nil nil]
    coll))

(defn deep-merge [& ms]
  (apply
    merge-with
    (fn [x y]
      (cond (map? y) (deep-merge x y)
            :else y))
    ms))

(defn remove-nil-empty [m]
  (into {} (remove (fn [[k v]]
                     (or (nil? v)
                         (and (coll? v) (empty? v)))) m)))

(defn remove-nils [m]
  (into {} (remove (comp nil? second) m)))

(defn deep-merge-some [& ms]
  (postwalk (fn [x]
              (if (map? x)
                (remove-nil-empty x)
                x))
            (apply deep-merge ms)))

(defn merge-some [& ms]
  (reduce
    (fn [m m']
      (let [[some-keys nil-keys] (split-by (comp some? m') (keys m'))]
        (as-> m x
          (merge x (select-keys m' some-keys))
          (apply dissoc x nil-keys))))
    ms))

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
  (into {} (map (fn [[k v]] (f k v)) xs)))

(defn doclines [_var]
  (when-some [_doc (:doc (meta _var))]
    (let [lines (str/split _doc #"\n")
          indent (->> (rest lines)
                      (map #(count (re-find #"^ *" %)))
                      (apply min ##Inf))]
      (capture lines indent)
      (map-indexed #(cond-> %2 (not (zero? %1)) (subs indent))
                   lines))))
