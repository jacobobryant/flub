(ns flub.core
  (:require
    [clojure.pprint :as pp]
    [clojure.set :as set]
    [clojure.spec.alpha :as s]
    [clojure.string :as str]
    [clojure.walk :refer [postwalk]]
    [clojure.tools.namespace.repl :as tn-repl]
    #?@(:clj [[clojure.java.shell :as shell]])))

(defonce system (atom nil))

(defn refresh []
  (let [{:keys [flub/after-refresh flub/stop]} @system]
    (doseq [f stop]
      (f))
    (tn-repl/refresh :after after-refresh)))

(defn start-system [config components]
  (reset! system (merge {:flub/stop '()} config))
  (reduce (fn [_ f]
            (reset! system (f @system)))
    nil
    components))

(defn read-env [env-keys]
  (->> env-keys
    (keep (fn [[env-key clj-key coerce]]
            (when-some [v (System/getenv env-key)]
              [clj-key ((or coerce identity) v)])))
    (into {})))

(defn map-kv [f m]
  (into {} (map (fn [[k v]] (f k v)) m)))

(defn map-keys [f m]
  (map-kv (fn [k v] [(f k) v]) m))

(defn map-vals [f m]
  (map-kv (fn [k v] [k (f v)]) m))

(defn map-from-to [f g xs]
  (->> xs
       (map (juxt f g))
       (into {})))

(defn map-from [f xs]
  (map-from-to f identity xs))

(defn map-to [f xs]
  (map-from-to identity f xs))

(defn pprint [x]
  (binding [*print-namespace-maps* false]
    (pp/pprint x)))

(defn ppr-str [x]
  (with-out-str (pprint x)))

(defn only-keys [& {:keys [req opt req-un opt-un]}]
  (let [all-keys (->> (concat req-un opt-un)
                   (map (comp keyword name))
                   (concat req opt))]
    (s/and
      map?
      #(= % (select-keys % all-keys))
      (eval `(s/keys :req ~req :opt ~opt :req-un ~req-un :opt-un ~opt-un)))))

(defn assoc-pred
  "Like assoc, but skip kv pairs where (f v) is false."
  [m f & kvs]
  (if-some [kvs (some->> kvs
                         (partition 2)
                         (filter (comp f second))
                         (apply concat)
                         not-empty)]
    (apply assoc m kvs)
    m))

(defn assoc-some [m & kvs]
  (apply assoc-pred m some? kvs))

(defn anomaly? [x]
  (s/valid? (s/keys :req [:cognitect.anomalies/category] :opt [:cognitect.anomalies/message]) x))

(defn anom [category & [message & kvs]]
  (apply assoc-some
    {:cognitect.anomalies/category (keyword "cognitect.anomalies" (name category))}
    :cognitect.anomalies/message message
    kvs))

(defn ns-contains? [nspace sym]
  (and (namespace sym)
    (let [segments (str/split (name nspace) #"\.")]
      (= segments (take (count segments) (str/split (namespace sym) #"\."))))))

(defn select-as [m key-map]
  (-> m
    (select-keys (keys key-map))
    (set/rename-keys key-map)))

(defn select-ns [m nspace]
  (select-keys m (filter #(ns-contains? nspace (symbol %)) (keys m))))

(defn ns-parts [nspace]
  (if (nil? nspace)
    []
    (some-> nspace
      str
      not-empty
      (str/split #"\."))))

(defn select-ns-as [m ns-from ns-to]
  (map-keys
    (fn [k]
      (let [new-ns-parts (->> (ns-parts (namespace k))
                           (drop (count (ns-parts ns-from)))
                           (concat (ns-parts ns-to)))]
        (if (empty? new-ns-parts)
          (keyword (name k))
          (keyword (str/join "." new-ns-parts) (name k)))))
    (select-ns m ns-from)))

(defn prepend-ns [ns-segment k]
  (keyword
    (cond-> ns-segment
      (not-empty (namespace k)) (str "." (namespace k)))
    (name k)))

(defn prepend-keys [ns-segment m]
  (map-keys #(prepend-ns ns-segment %) m))

(defn nest-string-keys [m ks]
  (let [ks (set ks)]
    (reduce (fn [resp [k v]]
              (let [nested-k (keyword (namespace k))]
                (if (ks nested-k)
                  (-> resp
                    (update nested-k assoc (name k) v)
                    (dissoc k))
                  resp)))
    m
    m)))

(defn add-seconds [date seconds]
  #?(:clj (java.util.Date/from (.plusSeconds (.toInstant date) seconds))
     :cljs (js/Date. (+ (.getTime date) (* 1000 seconds)))))

(defn concrete [x]
  (cond
    (var? x) @x
    (fn? x) (x)
    :default x))

(defn split-by [pred xs]
  (reduce #(update %1 (if (pred %2) 0 1) (fnil conj []) %2)
    [nil nil] xs))

(defn compare= [x y]
  (= 0 (compare x y)))

(defn compare< [x y]
  (= -1 (compare x y)))

(defn compare> [x y]
  (= 1 (compare x y)))

(defn compare<= [x y]
  (or (compare< x y) (compare= x y)))

(defn compare>= [x y]
  (or (compare> x y) (compare= x y)))

(defn join [sep xs]
  (butlast (interleave xs (repeat sep))))

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

(defn print-table
  "Prints a nicely formatted table.

  Example:
  ```
  (print-table
    [[:foo \"Foo\"] [:bar \"Bar\"]]
    [{:foo 1 :bar 2} {:foo 3 :bar 4}])
  => Foo  Bar
     1    2
     3    4
  ```"
  [header-info table]
  (let [[ks header] (apply map vector header-info)
        header (map #(str % "  ") header)
        body (->> table
                  (map (apply juxt ks))
                  (map (fn [row] (map #(str % "  ") row))))
        rows (concat [header] body)]
    (doseq [row (format-columns rows)]
      (println row))))

(defn between-hours? [t h1 h2]
  (let [hours (/ (mod (quot (inst-ms t) (* 1000 60)) (* 60 24)) 60.0)]
    (<= h1 hours h2)))

(defn day-of-week [t]
  (-> (inst-ms t)
    (quot (* 1000 60 60))
    (- (* 24 3) 8)
    (quot 24)
    (mod 7)))

(defn distinct-by [f coll]
  (let [step (fn step [xs seen]
               (lazy-seq
                 ((fn [[x :as xs] seen]
                    (when-let [s (seq xs)]
                      (let [fx (f x)]
                        (if (contains? seen fx)
                          (recur (rest s) seen)
                          (cons x (step (rest s) (conj seen fx)))))))
                  xs seen)))]
    (step coll #{})))

(def rfc3339 "yyyy-MM-dd'T'HH:mm:ss.SSSXXX")

#?(:clj
   (do
     (defn base64-encode [bs]
       (.encodeToString (java.util.Base64/getEncoder)
         bs))

     (defn base64-decode [s]
       (.decode (java.util.Base64/getDecoder) s))

     (defn parse-format-date [date in-format out-format]
       (cond->> date
         in-format (.parse (new java.text.SimpleDateFormat in-format))
         out-format (.format (new java.text.SimpleDateFormat out-format))))

     (defn parse-date
       ([date]
        (parse-date date rfc3339))
       ([date in-format]
        (parse-format-date date in-format nil)))

     (defn format-date
       ([date]
        (format-date date rfc3339))
       ([date out-format]
        (parse-format-date date nil out-format)))

     (defn last-midnight [t]
       (-> t
         inst-ms
         (quot (* 1000 60 60 24))
         (* 1000 60 60 24)
         (java.util.Date.)))

     (defn take-str [n s]
       (some->> s (take n) (str/join "")))

     (defn ellipsize [n s]
       (cond-> (take-str n s)
         (< n (count s)) (str "…")))

     (defn sppit [f x]
       (spit f (ppr-str x)))

     (defn sh
       "Runs a shell command.

       Returns the output if successful; otherwise, throws an exception."
       [& args]
       (let [result (apply shell/sh args)]
         (if (= 0 (:exit result))
           (:out result)
           (throw (ex-info (:err result) result)))))

     (defmacro sdefs [& body]
       `(do
          ~@(for [form (partition 2 body)]
              `(s/def ~@form))))

     (defmacro fix-stdout [& body]
       `(let [ret# (atom nil)
              s# (with-out-str
                   (reset! ret# (do ~@body)))]
          (some->> s#
            not-empty
            (.print System/out))
          @ret#))

     (defn add-deref [form syms]
       (postwalk
         #(cond->> %
            (syms %) (list deref))
         form))

     (defmacro letdelay [bindings & body]
       (let [[bindings syms] (->> bindings
                               (partition 2)
                               (reduce (fn [[bindings syms] [sym form]]
                                         [(into bindings [sym `(delay ~(add-deref form syms))])
                                          (conj syms sym)])
                                 [[] #{}]))]
         `(let ~bindings
            ~@(add-deref body syms))))

     (defmacro catchall [& body]
       `(try ~@body (catch Exception ~'_ nil)))

     (defmacro verbose [& body]
       `(try ~@body
          (catch Exception e#
            (.printStackTrace e#))))

     (defmacro pprint-ex [& body]
       `(try
          (bu/pprint ~@body)
          (catch ~'Exception e#
            (st/print-stack-trace e#)))))

   :cljs
   (do
     (defn chan? [x]
       (satisfies? (requiring-resolve 'cljs.core.async.impl.protocols/ReadPort) x))))
