(ns trident.cljs-http
  "Slight additions to `cljs-http`"
  (:require [cljs-http.client :as client]
            [cljs-http.core :as core]
            [cljs.core.async :as async]
            [cljs.reader :refer [read-string]]))

(defn wrap-edn-response
  "Decode application/edn responses.

  Passes `opts` to `cljs.reader/read-string`."
  [client opts]
  (fn [request]
    (async/map
      #(client/decode-body
         %
         (partial read-string (select-keys opts [:readers :default :eof]))
         "application/edn"
         (:request-method request))
      [(client request)])))

(defn wrap-request
  "Returns a batteries-included HTTP request function coresponding to the given
   core client. Passes `opts` to [[wrap-edn-response]]."
  [request {:keys [readers] :as opts}]
  (-> request
      client/wrap-accept
      client/wrap-form-params
      client/wrap-multipart-params
      client/wrap-edn-params
      (wrap-edn-response opts)
      client/wrap-transit-params
      client/wrap-transit-response
      client/wrap-json-params
      client/wrap-json-response
      client/wrap-content-type
      client/wrap-query-params
      client/wrap-basic-auth
      client/wrap-oauth
      client/wrap-method
      client/wrap-url
      client/wrap-channel-from-request-map
      client/wrap-default-headers))

(defn default-request [opts]
  (wrap-request core/request opts))
