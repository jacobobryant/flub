(ns trident.views.backend
  "Helper functions for working with hiccup"
  (:require [trident.views.macros :refer [defview]]))

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
  (into [:p (update opts :style #(merge {:max-width "450px"} %))] contents))

(defn youtube [opts]
  [:iframe (merge
             {:width "560"
              :height "315"
              :style {:border-width "1px"
                      :border-style "solid"
                      :border-color "black"}
              :frameborder "0"
              :allow "accelerometer; autoplay; encrypted-media; gyroscope; picture-in-picture"
              :allowfullscreen true}
             opts)])
