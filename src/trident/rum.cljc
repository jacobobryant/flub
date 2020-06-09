(ns trident.rum
  (:require
    [rum.core :as rum :refer [defc defcs reactive react]]))

(def html rum/render-static-markup)

(defn locals [& args]
  (->> args
    (partition 2)
    (map #(apply rum/local %))
    (apply merge-with comp)))

(defc form-text < reactive
  [{:keys [model label id placeholder] input-type :type}]
  [:.form-group
   [:label {:for (str id)} label]
   [:input.form-control
    {:id (str id)
     :placeholder placeholder
     :value (react model)
     :on-change #(reset! model (.. % -target -value))
     :type input-type}]])

(defcs text-with-button < (locals "" ::text)
  [{::keys [text]} {:keys [disabled placeholder on-click btn-text]}]
  [:.form-row
   [:.col-12.col-sm-9.mb-2.mb-sm-0
    [:input.form-control {:placeholder placeholder
                          :value @text
                          :on-change #(reset! text (.. % -target -value))}]]
   [:.col-12.col-sm-3
    [:button.btn.btn-primary.btn-block
     {:on-click #(on-click @text)
      :disabled (or (empty? @text) disabled)}
     btn-text]]])

(defn firebase-scripts [& ks]
  (let [ks (set ks)]
    (list
      [:script {:src "/__/firebase/7.4.0/firebase-app.js"}]
      (for [k ks
            :when (not= :ui k)]
        [:script {:src (str "/__/firebase/7.4.0/firebase-" (name k) ".js")}])
      [:script {:src "/__/firebase/init.js"}]
      (when (ks :ui)
        [:script {:src "/js/firebase-ui-auth-4.3.0.js"}]))))

(def ensure-logged-in
  [:script
   {:dangerouslySetInnerHTML
    {:__html "firebase.auth().onAuthStateChanged(u => { if (!u) window.location.href = '/login/'; });"}}])

(def ensure-logged-out
  [:script
   {:dangerouslySetInnerHTML
    {:__html "firebase.auth().onAuthStateChanged(u => { if (u) window.location.href = '/app/'; });"}}])

(def loading
  [:div.d-flex.justify-content-center
   [:div.spinner-border.text-primary.mx-auto
    {:role "status"}
    [:span.sr-only "Loading..."]]])

(defn unsafe [m html]
  (merge m {:dangerouslySetInnerHTML {:__html html}}))
