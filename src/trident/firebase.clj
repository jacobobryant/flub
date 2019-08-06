(ns trident.firebase
  "Functions for authenticating Firebase user tokens."
  (:require [clojure.java.io :refer [input-stream]])
  (:import com.google.firebase.auth.FirebaseAuth
           [com.google.firebase FirebaseApp FirebaseOptions$Builder]
           com.google.auth.oauth2.GoogleCredentials))

(defn init-firebase!
  "Calls `com.google.firebase.FirebaseApp/initializeApp`.

  - `firebase-key`: the contents of your `*-firebase-adminsdk-*.json` file
  - `app-name`: the name to initialize this app under. See
    [[com.google.firebase.FirebaseApp/initializeApp]]"
  [firebase-key app-name]
  (let [credentials (-> firebase-key
                        (.getBytes)
                        input-stream
                        (GoogleCredentials/fromStream))
        options (-> (new FirebaseOptions$Builder)
                    (.setCredentials credentials)
                    .build)]
    (FirebaseApp/initializeApp options app-name)))

(defn app-instance [firebase-key]
  (let [app-name (str (hash firebase-key))]
    (try
      (FirebaseApp/getInstance app-name)
      (catch IllegalStateException _
        (init-firebase! firebase-key app-name)))))

(defn auth-instance [firebase-key]
  (FirebaseAuth/getInstance
    (app-instance firebase-key)))

(defn verify-token
  "Verifies a firebase token, returning a map of the claims.

  - `token`: the user's auth token.
  - `firebase-key`: the contents of your `*-firebase-adminsdk-*.json` file
  (a string)

  Returns `nil` if `token` is invalid. If needed, initializes the firebase app
  with the hash of `firebase-key` as the app name. See [[init-firebase!]]."
  [token firebase-key]
  (try
    (into {}
          (-> (auth-instance firebase-key)
              (.verifyIdToken token)
              (.getClaims)))
    (catch Exception _ nil)))
