(ns trident.views.backend
  "Helper functions for working with hiccup"
  (:require [trident.views :refer [defview]]))

(defn gap
  "Returns a 10px (by default) hiccup div."
  ([size]
   [:div {:style {:width size
                  :height size}}])
  ([]
   (gap "10px")))

(defn v-box
  "Puts `children` in a vertical div with space in between each child."
  [& children]
  (into
    [:div {:style {:display "flex"
                   :flex-direction "column"}}]
    (interpose (gap) (filter some? children))))

(defview ^{:doc "Returns a hiccup p element with a max width of 450px."}
  p
  [opts & contents]
  (into [:p.mx-auto (update opts :style #(merge {:max-width "450px"} %))] contents))
