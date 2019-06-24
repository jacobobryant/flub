(ns trident.firebase
  "Functions for authenticating Firebase user tokens."
  (:require [clojure.java.io :refer [input-stream]])
  (:import com.google.firebase.auth.FirebaseAuth
           [com.google.firebase FirebaseApp FirebaseOptions$Builder]
           com.google.auth.oauth2.GoogleCredentials))

; TODO make this reloadable
(defn init-firebase!
  "Calls `com.google.firebase.FirebaseApp/initializeApp`.

  `firebase-key`: your Firebase private key (a string)"
  [firebase-key]
  (let [credentials (-> firebase-key
                        (.getBytes)
                        input-stream
                        (GoogleCredentials/fromStream))
        options (-> (new FirebaseOptions$Builder)
                    (.setCredentials credentials)
                    .build)]
    (FirebaseApp/initializeApp options)))

(defn verify-token
  "Verifies a firebase token, returning a map of the claims.

   - `token`: the user's auth token.
   - `firebase-key`: the contents of your `*-firebase-adminsdk-*.json` file
     (a string)

  If needed, calls [[init-firebase!]]. Returns `nil` if `token` is invalid."
  [token firebase-key]
  (when (= 0 (count (FirebaseApp/getApps)))
    (init-firebase! firebase-key))
  (try
    (into {}
          (-> (FirebaseAuth/getInstance)
              (.verifyIdToken token)
              (.getClaims)))
    (catch Exception e nil)))
