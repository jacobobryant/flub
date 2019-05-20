(ns jobryant.firebase
  (:require [clojure.java.io :refer [input-stream]])
  (:import com.google.firebase.auth.FirebaseAuth
           [com.google.firebase FirebaseApp FirebaseOptions$Builder]
           com.google.auth.oauth2.GoogleCredentials))

(defn init-firebase! [firebase-key]
  (let [credentials (-> firebase-key
                        (.getBytes)
                        input-stream
                        (GoogleCredentials/fromStream))
        options (-> (new FirebaseOptions$Builder)
                    (.setCredentials credentials)
                    .build)]
    (FirebaseApp/initializeApp options)))

(defn verify-token [token get-key]
  (when (= 0 (count (FirebaseApp/getApps)))
    (init-firebase! (get-key)))
  (try
    (into {}
          (-> (FirebaseAuth/getInstance)
              (.verifyIdToken token)
              (.getClaims)))
    (catch Exception e nil)))
