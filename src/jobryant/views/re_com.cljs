(ns jobryant.views.re-com
  (:refer-clojure :exclude [case])
  (:require [re-com.core :as rc]
            [clojure.pprint :refer [pprint]])
  (:require-macros [jobryant.util :as u]
                   [jobryant.views.re-com]))

(u/cljs-pullall re-com.core
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

(defn flat [x]
  (mapcat #(if (coll? (first %)) % [%]) x))

(defn hv-box [box props & children]
  (if (map? props)
    (reduce into [box :children (flat children)]
            (merge {:gap "10px"} props))
    [box :gap "10px" :children (flat (into [props] children))]))

(def v-box (partial hv-box rc/v-box))
(def h-box (partial hv-box rc/h-box))

(defn grow []
  [:div {:style {:flex-grow 1}}])

(defn input-text [& {:keys [model transform] :or {transform identity} :as props}]
  (reduce into [rc/input-text]
          (merge {:on-change #(reset! model (transform %))
                  :change-on-blur? (not (contains? props :transform))}
                 (dissoc props :transform))))

(defn horizontal-tabs [& {:keys [model] :as props}]
  (reduce into [rc/horizontal-tabs]
          (merge {:on-change #(reset! model %)} props)))

(defn gap [& {:as props}]
  (reduce into [rc/gap] (merge {:size "10px"} props)))

(defn case [x & clauses]
  [:div
   (for [[x' component] (partition 2 clauses)]
     ^{:key x'} [:div (when (not= x x') {:style {:display "none"}})
                 component])])
