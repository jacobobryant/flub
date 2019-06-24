(ns trident.util-test
  (:require [clojure.test :as t]
            [trident.util :as u]))

(t/deftest derive-config-test
  (let [m {:foo "J"
           :first-name ^:derived #(str (u/get-config % :foo) "ohn")
           :last-name  "Doe"
           :full-name ^:derived #(str (u/get-config % :first-name) " "
                                      (u/get-config % :last-name))}]
    (t/is (= (:full-name (u/derive-config m)) "John Doe"))))
