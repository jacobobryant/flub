(ns trident.firebase
  "Functions for authenticating Firebase user tokens."
  (:require [clojure.java.io :refer [input-stream]])
  (:import com.google.firebase.auth.FirebaseAuth
           [com.google.firebase FirebaseApp FirebaseOptions$Builder]
           com.google.auth.oauth2.GoogleCredentials))

; TODO make this reloadable
(defn init-firebase!
  "Calls `com.google.firebase.FirebaseApp/initializeApp`.

   - `firebase-key`: your Firebase private key (a string)
   - `app-name`: the name to initialize this app under. See
     [[com.google.firebase.FirebaseApp/initializeApp]]"
  [firebase-key app-name]
  (let [credentials (-> firebase-key
                        (.getBytes)
                        input-stream
                        (GoogleCredentials/fromStream))
        _ (def credentials credentials)
        options (-> (new FirebaseOptions$Builder)
                    (.setCredentials credentials)
                    .build)]
    (FirebaseApp/initializeApp options app-name)))

(defn verify-token
  "Verifies a firebase token, returning a map of the claims.

  - `token`: the user's auth token.
  - `firebase-key`: the contents of your `*-firebase-adminsdk-*.json` file
  (a string)

  Returns `nil` if `token` is invalid. If needed, initializes the firebase app
  with the hash of `firebase-key` as the app name. See [[init-firebase!]]."
  [token firebase-key]
  (let [app-name (str (hash firebase-key))
        app (try
              (FirebaseApp/getInstance app-name)
              (catch IllegalStateException _
                (init-firebase! firebase-key app-name)))]
    (try
      (into {}
            (-> app
                (FirebaseAuth/getInstance)
                (.verifyIdToken token)
                (.getClaims)))
      (catch Exception _ nil))))
