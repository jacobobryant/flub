(ns trident.views
  "Utilities for working with frontend views.")

(defn ^:no-doc get-opts [args]
  (cond-> args
    (-> args first map? not) (conj nil)))

(defmacro defview
  "Defines a function that optionally takes a map as the first argument.

  This is similar to Reagent components. Example:
  ```
  (defview my-view [opts foo bar]
    [opts foo bar])

  (my-view {:some-opt 1} \"a\" \"b\")
  => [{:some-opt 1} \"a\" \"b\"]
  (my-view \"a\" \"b\")
  => [nil \"a\" \"b\"]
  ```"
  [f [opts & args] & forms]
  `(defn ~f [& args#]
     (let [[~opts ~@args] (get-opts args#)]
       ~@forms)))
