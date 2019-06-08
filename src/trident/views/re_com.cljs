(ns trident.views.re-com
  "Drop-in replacement for `re-com.core` with a few changes."
  (:refer-clojure :exclude [case])
  (:require [re-com.core :as rc]
            [clojure.pprint :refer [pprint]])
  (:require-macros [trident.util :as u]
                   [trident.views.re-com]))

(u/cljs-import-vars re-com.core
                    align-style horizontal-pill-tabs row-button popover-border border
                    modal-panel start-tour alert-list p input-textarea h-split
                    slider make-tour make-tour-nav flex-flow-style progress-bar
                    selection-list scroller radio-button checkbox p-span
                    button close-button box alert-box datepicker input-password
                    typeahead info-button vertical-bar-tabs justify-style
                    popover-content-wrapper title flex-child-style
                    horizontal-bar-tabs v-split single-dropdown hyperlink-href
                    md-icon-button popover-tooltip line label
                    scroll-style input-time vertical-pill-tabs throbber
                    datepicker-dropdown popover-anchor-wrapper md-circle-icon-button
                    hyperlink)

(defn ^:no-doc flat [x]
  (mapcat #(if (coll? (first %)) % [%]) x))

(defn ^:no-doc hv-box [box props & children]
  (if (map? props)
    (reduce into [box :children (flat children)]
            (merge {:gap "10px"} props))
    [box :gap "10px" :children (flat (into [props] children))]))

(def ^{:doc
"Like `re-com.core/v-box` but without map destructuring.

Example:
```
[v-box {:style {:width \"200px\"}}
  [child-component-1]
  [child-component-2]]
```"} v-box (partial hv-box rc/v-box))
(def ^{:doc "See [[v-box]]"} h-box (partial hv-box rc/h-box))

(defn grow
  "A div with `:flex-grow 1`."
  []
  [:div {:style {:flex-grow 1}}])

(defn input-text
  "Like `re-com.core/input-text`, but stores `:on-change` results in `model`.

   - `model`: an atom
   - `transform`: a function that will wrap `:on-change` values before they're
     stored in `model`."
  [& {:keys [model transform] :or {transform identity} :as props}]
  (reduce into [rc/input-text]
          (merge {:on-change #(reset! model (transform %))
                  :change-on-blur? (not (contains? props :transform))}
                 (dissoc props :transform))))

(defn horizontal-tabs
  "Like `re-com.core/horizontal-tabs`, but stores `:on-change` results in `model`."
  [& {:keys [model] :as props}]
  (reduce into [rc/horizontal-tabs]
          (merge {:on-change #(reset! model %)} props)))

(defn gap
  "Like `re-com.core/gap`, but with a default `:size` of `10px`."
  [& {:as props}]
  (reduce into [rc/gap] (merge {:size "10px"} props)))

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
