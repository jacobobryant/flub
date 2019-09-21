(ns trident.views
  "Utilities for working with frontend views."
  (:refer-clojure :exclude [case])
  (:require [trident.views.macros :refer [defview]]))

(defn gap
  "Returns a 10px (by default) div."
  ([size]
   [:div {:style {:width size
                  :height size}}])
  ([]
   (gap "10px")))

(defview h-box [{:keys [style gap-size]} & contents]
  (into [:div {:style (merge {:display "flex"} style)}]
        (interpose (if gap-size (gap gap-size) (gap))
                   (filter some? contents))))

(defview v-box [opts & contents]
  (apply h-box (assoc-in opts [:style :flex-direction] "column") contents))

(defn case
  "Similar to `clojure.core/case`, but wraps the values in divs.

  Values that aren't displayed will still be returned, but they'll be wrapped
  in a div with `:display \"none\"` for performance.

  Example:
  ```
  (def tab (reagent.core/ratom ::tab-1))
  (case @tab
    ::tab-1 [:p \"tab 1\"]
    ::tab-2 [:p \"tab 2\"])
  => [:div
       (^{:key ::tab-1} [:div [:p \"tab 1\"]]
        ^{:key ::tab-2} [:div {:style {:display \"none\"}}
                              [:p \"tab 2\"]])]
  ```"
  [x & clauses]
  [:div
   (for [[x' component] (partition 2 clauses)]
     ^{:key x'} [:div (when (not= x x') {:style {:display "none"}})
                 component])])
