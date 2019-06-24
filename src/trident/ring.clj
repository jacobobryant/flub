(ns trident.ring
  "Some Ring middleware"
  (:require [clojure.pprint :refer [pprint]]
            [clojure.walk :as walk]
            [taoensso.timbre :refer [error debug]]
            [mount.core :as mount]))

(defn wrap-uid
  "Authenticates the request, or gives a 401 response.

  Takes the value of the Authorization header and passes it to `verify-token`,
  which should return a map of claims (e.g. `{\"user_id\": \"foo\", \"email\":
  \"alice@example.com\"}`).

  The following keys will be added to the request: `:claims` (the return value
  of `verify-token`) and `:uid` (`(get claims uid-key)`, for convenience)."
  [handler {:keys [verify-token uid-key] :or {uid-key "user_id"}}]
  (fn [{:keys [request-method] :as req}]
    (if (= :options request-method)
      (handler req)
      (if-some [claims (some-> req
                               (get-in [:headers "authorization"])
                               (subs 7)
                               verify-token)]
        (handler (assoc req :claims claims :uid (get claims uid-key)))
        {:status 401
         :headers {"Content-Type" "text/plain"}
         :body "Invalid authentication token."}))))

(defn wrap-catchall
  "Catches and logs exceptions."
  [handler]
  (fn [req]
    (try (handler req)
         (catch Exception e
           (error {:msg "Unhandled exception in handler" :ex e})
           {:status 500}))))

(defn wrap-mount
  "Ensures that `mount.core/start` has been called."
  [handler]
  (fn [req]
    (mount/start)
    (handler req)))

(defn wrap-request [handler & fs]
  (apply comp handler fs))

(defn wrap-debug
  "Logs requests and responses with `taonsso.timbre/debug`."
  ([handler {:keys [exclude]}]
   (let [shorten (fn [x]
                   (->> (apply dissoc x exclude)
                        (walk/postwalk #(if (vector? %) (vec (take 10 %)) %))))]
     (fn [req]
       (def req req)
       (println "request:")
       (pprint (shorten req))
       (let [response (handler req)]
         (def response response)
         (println "\nresponse:")
         (pprint (shorten response))
         (println)
         response))))
  ([handler]
   (wrap-debug handler nil)))
