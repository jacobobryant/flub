(ns flub.extra
  (:require
    [buddy.sign.jwt :as jwt]
    [clj-http.client :as http]
    [flub.core :as flub]
    [lambdaisland.uri :as uri]))

(defn jwt-encrypt [claims secret]
  (jwt/encrypt
    (-> claims
      (assoc :exp (flub/add-seconds (java.util.Date.) (:exp-in claims)))
      (dissoc :exp-in))
    (flub.core/base64-decode secret)
    {:alg :a256kw :enc :a128gcm}))

(defn jwt-decrypt [token secret]
  (flub/catchall
    (jwt/decrypt
      token
      (flub.core/base64-decode secret)
      {:alg :a256kw :enc :a128gcm})))

(defn assoc-url [url & kvs]
  (str (apply uri/assoc-query url kvs)))

(defn send-mailgun [{:mailgun/keys [api-key endpoint from]} opts]
  (try
    (http/post endpoint
      {:basic-auth ["api" api-key]
       :form-params (merge {:from from} opts)})
    true
    (catch Exception e
      (println "send-mailgun failed:" (:body (ex-data e)))
      false)))
