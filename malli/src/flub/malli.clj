(ns flub.malli
  (:refer-clojure :exclude [assert])
  (:require
    [flub.core :as flub]
    [malli.core :as m]
    [malli.error :as me]))

(defn assert [schema x opts]
  (when-not (m/validate schema x opts)
    (throw
      (ex-info "Invalid schema."
        {:value x
         :schema schema
         :explain (me/humanize (m/explain schema x opts))}))))
