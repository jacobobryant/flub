(ns jobryant.views.re-com
  (:refer-clojure :exclude [for])
  (:require [jobryant.views :refer [defview]]))

(defmacro for [bindings body]
  `(clojure.core/for ~bindings
     (with-meta ~body {:key ~(first bindings)})))

(defn gap
  ([size]
   [:div {:style {:width size
                  :height size}}])
  ([]
   (gap "10px")))

(defn v-box [& children]
  (into
    [:div {:style {:display "flex"
                   :flex-direction "column"}}]
    (interpose (gap) (filter some? children))))

(defview p [opts contents]
  (into [:p.mx-auto (update opts :style #(merge {:max-width "450px"} %))] contents))
