(ns trident.jwt
  (:require
    [cemerick.url :as url]
    [ring.middleware.token :as token]
    [trident.util :as u])
  (:import
    [com.auth0.jwt.algorithms Algorithm]
    [com.auth0.jwt JWT]))

(defn encode [claims {:keys [secret alg]}]
  ; todo add more algorithms
  (let [alg (case alg
              :HS256 (Algorithm/HMAC256 secret))]
    (->
      (reduce (fn [token [k v]]
                (.withClaim token (name k) v))
        (JWT/create)
        claims)
      (.sign alg))))

(def decode token/decode)

(defn mint [{:keys [secret expires-in iss]} claims]
  (encode
    (u/assoc-some claims
      :iss iss
      :iat (u/now)
      :exp (some->> expires-in (u/add-seconds (u/now))))
    {:secret secret
     :alg :HS256}))

(defn url [{:keys [url claims iss expires-in jwt-secret]}]
  (let [jwt (mint {:secret jwt-secret
                   :expires-in expires-in
                   :iss iss}
              claims)]
    (-> url
      url/url
      (assoc :query {:token jwt})
      str)))
