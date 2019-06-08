(ns trident.ring
  "Some Ring middleware"
  (:require [taoensso.timbre :refer [error debug]]
            [mount.core :as mount]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
            [ring.middleware.format-params :refer [wrap-clojure-params]]
            [ring.middleware.cors :refer [wrap-cors]]))

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
  "Ensures that `mount.core/start` has been called.

  `state-var`: the var of a mount component, used for checking if mount has been
  started already."
  [handler state-var]
  (fn [req]
    (when (contains? #{mount.core.NotStartedState mount.core.DerefableState} (type @state-var))
      (debug {:msg "Started mount" :result (mount/start)}))
    (handler req)))

(defn wrap-debug-requests
  "Logs requests and responses with `taonsso.timbre/debug`."
  [handler]
  (fn [req]
    (debug {:msg "got request" :uri (:uri req)})
    (let [response (handler req)]
      (debug {:msg "request response"
              :request-params (:params req)
              :response-body (:body response)})
      response)))

(defn wrap-trident-defaults
  "Wraps `handler` with a bunch of middleware.

  Includes all the middleware in this namespace, plus
  `ring.middleware.cors/wrap-cors`,
  `ring.middleware.format-params/wrap-clojure-params` and
  `ring.middleware.defaults/wrap-defaults` (with `api-defaults`).

   - `uid-opts`: see [[wrap-uid]]
   - `state-var`: see [[wrap-mount]]
   - `origins`: passed to `wrap-cors` for `:access-control-allow-origin`"
  [handler {:keys [uid-opts state-var origins]}]
  (-> handler
      (wrap-uid uid-opts)
      wrap-debug-requests
      (wrap-cors
        :access-control-allow-origin origins
        :access-control-allow-methods [:get :post]
        :access-control-allow-headers ["Authorization" "Content-Type"])
      wrap-clojure-params
      (wrap-defaults api-defaults)
      (wrap-mount state-var)
      wrap-catchall))
