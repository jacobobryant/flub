(ns trident.util
  (:require [trident.util.core :as u]
            [trident.util.datomic]
  #?@(:clj [[potemkin :refer [import-vars]]
            [trident.util.ring]]))
  #?(:cljs (:require-macros [trident.util])))

#?(:clj (u/pullall trident.util.core trident.util.datomic trident.util.ring))

#?(:cljs (do

; see if we can restrict this to fns that are only defined for cljs
(u/cljs-pullall trident.util.datomic
                datascript-schema
                datomic-schema
                ent-spec
                expand-flags
                stringify-eids
                translate-eids
                wrap-vec)

(u/cljs-pullall trident.util.core
                assoc-some
                c+
                c-
                conj-some
                cop
                deep-merge
                deep-merge-some
                dissoc-by
                format
                indexed
                instant?
                map-from
                map-inverse
                merge-some
                ord
                parse-int
                pprint
                pred->
                rand-str
                remove-nil-empty
                remove-nils
                split-by
                to-chan
                zip)

))
