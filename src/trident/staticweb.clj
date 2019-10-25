(ns trident.staticweb
  (:require [garden.core :as garden]
            [hiccup.core :as hiccup]
            [clojure.walk :refer [postwalk]]))

(defn css
  "Converts a standalone style map to css with `garden`.

  See [[html]]."
  [style]
  (let [s (garden/css {:pretty-print? false} [:foo style])]
    (subs s 4 (- (count s) 1))))

(defn html
  "Wraps `hiccup.core/html`, allowing you to define inline-styles like in Reagent.

  Example:
  ```
  (html [:div {:style {:color \"black\"}}])
  => \"<div style=\\\"color:black\\\"></div>\"
  ```"
  [form]
  (->> form
       (postwalk
         (fn [form]
           (if (and (vector? form)
                    (map? (second form))
                    (map? (get-in form [1 :style])))
             (update-in form [1 :style] css)
             form)))
       hiccup/html))
