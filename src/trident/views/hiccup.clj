(ns trident.views.hiccup
  (:require [garden.core :as garden]
            [hiccup.core :as hiccup]
            [clojure.walk :refer [postwalk]]))

(defn css [style]
  (let [s (garden/css {:pretty-print? false} [:foo style])]
    (-> s
        (subs 4 (- (count s) 1)))))

(defn html [form]
  (->> form
       (postwalk
         (fn [form]
           (if (and (vector? form)
                    (map? (second form))
                    (map? (get-in form [1 :style])))
             (update-in form [1 :style] css)
             form)))
       hiccup/html))
