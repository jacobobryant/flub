(ns trident.views
  "Utilities for working with frontend views."
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
