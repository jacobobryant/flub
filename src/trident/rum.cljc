(ns trident.rum
  (:require
    [rum.core :as rum :refer [defc defcs reactive react]]))

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

(defc head* [{:keys [title description]}]
  [:head
   [:title title]
   [:meta {:name "description"
           :content description}]
   [:meta {:charset "utf-8"}]
   [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
   [:link {:rel "stylesheet" :href "/css/bootstrap.css"}]
   [:link {:rel "stylesheet" :href "/css/main.css"}]
   [:link {:rel "apple-touch-icon" :sizes "180x180" :href "/apple-touch-icon.png"}]
   [:link {:rel "icon" :type "image/png" :sizes "32x32" :href "/favicon-32x32.png"}]
   [:link {:rel "icon" :type "image/png" :sizes "16x16" :href "/favicon-16x16.png"}]
   [:link {:rel "manifest" :href "/site.webmanifest"}]
   [:link {:href "https://fonts.googleapis.com/icon?family=Material+Icons" :rel "stylesheet"}]])

(defc navbar* [{:keys [app-name]}]
  [:nav.navbar.navbar-expand.navbar-dark.bg-primary
   [:.container
    [:a.navbar-brand {:style {:font-size "30px"}
                      :href "/"}
     app-name]
    [:div
     [:ul.navbar-nav.ml-auto
      [:li.nav-item
       [:a.nav-link {:href "/about/"} "About"]]]]]])

(defc base-page [opts & contents]
  (let [{:keys [navbar head scripts]
         :or {head (head* opts)
              navbar (navbar* opts)}} opts]
    [:html {:lang "en-US"
            :style {:min-height "100%"}}
     head
     (into
       [:body {:style {:min-height "100%"
                       :font-size "16px"
                       :font-family "\"Helvetica Neue\",Helvetica,Arial,sans-serif"}}
        navbar
        contents]
       scripts)]))

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
