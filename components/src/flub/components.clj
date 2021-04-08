(ns flub.components
  (:require
    [nrepl.server :as nrepl]
    [reitit.ring :as reitit]
    [ring.adapter.jetty9 :as jetty]))

(defn nrepl [{:keys [flub/first-start
                     flub.nrepl/port
                     flub.nrepl/quiet]
              :or {port 7888}
              :as sys}]
  (when first-start
    (nrepl/start-server :port port)
    (spit ".nrepl-port" (str port))
    (when-not quiet
      (println "nrepl running on port" port)))
  sys)

(defn reitit [{:flub.reitit/keys [routes default-handlers]
               :as sys}]
  (let [router (reitit/router routes)]
    (assoc sys
      :flub.reitit/router router
      :flub.web/handler (reitit/ring-handler
                          router
                          (apply reitit/routes default-handlers)))))

(defn jetty [{:flub.web/keys [host port handler]
              :flub.jetty/keys [quiet]
              :or {host "0.0.0.0"
                   port 8080}
              :as sys}]
  (let [server (jetty/run-jetty handler
                 {:host host
                  :port port
                  :join? false
                  :allow-null-path-info true})]
    (when-not quiet
      (println "Jetty running on" (str "http://" host ":" port)))
    (update sys :flub/stop conj #(jetty/stop-server server))))
