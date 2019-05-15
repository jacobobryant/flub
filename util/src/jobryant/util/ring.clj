(ns jobryant.util.ring
  (:require [taoensso.timbre :refer [error debug]]
            [mount.core :as mount]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
            [ring.middleware.format-params :refer [wrap-clojure-params]]
            [ring.middleware.cors :refer [wrap-cors]]))

(defn wrap-uid [handler {:keys [verify-token uid-key]
                         :or {uid-key "user_id"}}]
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

(defn wrap-catchall [handler]
  (fn [req]
    (try (handler req)
         (catch Exception e
           (error {:msg "Unhandled exception in handler" :ex e})
           {:status 500}))))

(defn wrap-mount [handler state-var]
  (fn [req]
    (when (contains? #{mount.core.NotStartedState mount.core.DerefableState} (type @state-var))
      (debug {:msg "Started mount" :result (mount/start)}))
    (handler req)))

(defn wrap-debug-requests [handler]
  (fn [req]
    (debug {:msg "got request" :uri (:uri req)})
    (let [response (handler req)]
      (debug {:msg "request response"
              :request-params (:params req)
              :response-body (:body response)})
      response)))

(defn wrap-ion-defaults [handler {:keys [state-var origins uid-opts]}]
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
