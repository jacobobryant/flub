# trident.firestore

trident.firestore is a ClojureScript wrapper for Firebase's [Cloud
Firestore](https://firebase.google.com/docs/firestore).

## Table of Contents

 - [Why Firestore?](#why-firestore)
 - [Usage](#usage)
   - [Write data](#write-data)
   - [Subscribe to queries](#subscribe-to-queries)
   - [Composing queries](#composing-queries)
   - [Reading data](#reading-data-without-subscribing)
   - [Subcollections](#subcollections)
   - [Coercions](#coercions)
   - [Using from Node](#using-from-node)
 - [Status](#status)
 - [Self-promotion](#self-promotion)
 - [License](#license)

## Why Firestore?

Here are the points most important to me as a solo developer:

Pros:

 - Clients can subscribe to queries and get notified of changes => simplifies
   client code
 - Idiomatic support for modeling graph data
 - Good usability/ease of use (like the rest of Firebase)
 - There's a decent rules system baked in, so you don't have to set up any
   backend endpoints to handle authorization

A note on query subscriptions: even if you don't need to subscribe to data written
by other users, it's still helpful to subscribe to data that the current user writes.
Then you can write the data once (to the remote DB) and let Firestore write it to
your client-side db (presumably an atom). Firestore will even handle optimistic
writes and rollbacks.

Cons:

 - Queries are pretty weak compared to SQL, let alone Datalog (e.g. no joins)
 - No DB-as-a-value or time travel features like in Datomic et. al.
 - No data/code locality like in Datomic Ions/On-Prem

I decided to try Firestore instead of Datomic when I started on the latest
iteration of [Findka](https://findka.com), and I've been pleased enough that Firestore is now my
go-to database for future projects. I'll reserve Datomic or Crux for times when
I need the extra features.

## Usage

```clojure
trident/firestore {:mvn/version "0.3.1"}
```

At some point you should read the [Firestore docs](https://firebase.google.com/docs/firestore).

The following require is assumed:

```clojure
(require '[trident.firestore :refer [write subscribe merge-changeset pull doc-exists? query]])
```


### Write data

Writes are modeled as "changesets": a map from idents to documents.
An ident is a vector that includes a collection name and a document ID.

```clojure
(write (js/firebase.firestore)
  {[:users "alice-user-id"] {:name "Alice"
                             :likes-tennis true}})

; Equivalent to:
(.. (js/firebase.firestore)
  (collection "users")
  (doc "alice-user-id")
  (set #js {:name "Alice"
            :likes-tennis true}))
```

The return value is a channel that receives a value (`nil`) when all the
documents have been written:
```clojure
(go
  (<! (write (js/firebase.firestore)
        {[:users "alice-user-id"] {:name "Alice"}
         [:users "bob-user-id"] {:name "Bob"}}))
  (println "done"))
```

You can omit the document ID to let Firestore create a new document with a
random ID:
```clojure
(write (js/firebase.firestore)
  {[:users] {:name "Alice"}})
```

By default, documents are replaced completely:
```clojure
(write (js/firebase.firestore)
  {[:users "alice-user-id"] {:name "Alice"}})
...
(write (js/firebase.firestore)
  {[:users "alice-user-id"] {:favorite-color "Blue...no, yellow!"}})
; The document will no longer include :name -- probably not what we wanted.
```

You can merge instead by setting metadata. The document will be created
if it doesn't exist:
```clojure
(write (js/firebase.firestore)
  {[:users "alice-user-id"] ^:merge {:favorite-color "Blue...no, yellow!"}})
```

`^:update` is like `^:merge`, but the operation will go through only if the document
already exists (otherwise it's no-op).
```clojure
(write (js/firebase.firestore)
  {[:users "alice-user-id"] ^:update {:favorite-color "Blue...no, yellow!"}})
```

You can delete documents by setting them to nil:
```clojure
(write (js/firebase.firestore)
  {[:users "alice-user-id"] nil})
```

**Note**: [There's a bug](https://clojure.atlassian.net/browse/ASYNC-192)
which makes reader macro metadata get clobbered by `go`. The `write` macro protects you
from this by wrapping its arguments in a function call:
```clojure
(write (js/firebase.firestore)
  {[:users "alice-user-id"] ^:merge {:favorite-color "Blue...no, yellow!"}})

; Expands to:
((fn []
   (trident.firestore.util/write-unsafe (js/firebase.firestore)
     {[:users "alice-user-id"] ^:merge {:favorite-color "Blue...no, yellow!"}})))
```
This means that including `<!` in calls to `write` will fail, so do this
instead:
```clojure
(go
  (let [color (<! (get-favorite-color))]
    (write (js/firebase.firestore)
      {[:users "alice-user-id"] ^:merge {:favorite-color color}})))
```

Though I haven't shown examples here, Firestore also supports arrays and nested maps.

### Subscribe to queries

`subscribe` takes a collection of queries and returns a channel that receives
the changesets. For convenience, documents will include an `:ident` attribute:
```clojure
(write (js/firebase.firestore)
  {[:users "alice-user-id"] {:name "Alice"}})

(let [c (subscribe (js/firebase.firestore)
          [{:ident [:users "alice-user-id"]}])]
  (go-loop []
    (prn (<! c))
    (recur)))
; Prints immediately:
; => {[:users "alice-user-id"] {:ident [:users "alice-user-id"]
;                               :name "Alice"}}

(write (js/firebase.firestore)
  {[:users "alice-user-id"] ^:merge {:color "blue"}})
; => {[:users "alice-user-id"] {:ident [:users "alice-user-id"]
;                               :name "Alice"
;                               :color "blue"}}

(write (js/firebase.firestore)
  {[:users "alice-user-id"] nil})
; => {[:users "alice-user-id"] nil}
```

`merge-changeset` goes well with `subscribe`:
```clojure
(write (js/firebase.firestore)
  {[:users "alice-user-id"] {:name "Alice"}})

(def db (atom {}))

(let [c (subscribe (js/firebase.firestore)
          [{:ident [:users "alice-user-id"]}])]
  (go-loop []
    (swap! db merge-changeset (<! c))
    (prn @db)
    (recur)))
; Prints immediately:
; => {:users {"alice-user-id" {:ident [:users "alice-user-id"]
;                              :name "Alice"}}}

(write (js/firebase.firestore)
  {[:users "alice-user-id"] ^:merge {:color "blue"}})
; => {:users {"alice-user-id" {:ident [:users "alice-user-id"]
;                              :name "Alice"
;                              :color "blue"}}}

(write (js/firebase.firestore)
  {[:users "alice-user-id"] nil})
; => {:users {}}
```

You can end subscriptions by closing the associated channel.

### Composing queries

You can subscribe to all the documents in a collection by omitting the
ident's document ID:
```clojure
(subscribe (js/firebase.firestore)
  [{:ident [:users]}])
```

If you're just subscribing to an ident, you can omit the map:
```clojure
(subscribe (js/firebase.firestore)
  [[:users]
   [:items "some-item-id"]])
```

You can add where clauses:
```clojure
(subscribe (js/firebase.firestore)
  [{:ident [:users]
    :where [[:color '== "blue"]
            [:age '<= 30]]}])
```

Sort:
```clojure
(subscribe (js/firebase.firestore)
  [{:ident [:users]
    :order-by [:age]}])

(subscribe (js/firebase.firestore)
  [{:ident [:users]
    :order-by [:age :color]}])

(subscribe (js/firebase.firestore)
  [{:ident [:users]
    :order-by [[:age "desc"] :color]}])
```

Paginate:
```clojure
(subscribe (js/firebase.firestore)
  [{:ident [:users]
    :order-by [:color]
    :limit 10}])

(subscribe (js/firebase.firestore)
  [{:ident [:users]
    :order-by [:color]
    :limit 10
    :start-at "blue"}])

(subscribe (js/firebase.firestore)
  [{:ident [:users]
    :order-by [:age :color]
    :limit 10
    :start-at [30 "blue"]}])
```

Also supported: `:limit-to-last`, `:start-after`, `:end-at`, `:end-after`

**Not yet supported**: including a document as the value of `:start-at` et. al.,
for example:
```clojure
(subscribe (js/firebase.firestore)
  [{:ident [:users]
    :order-by [:age :color]
    :limit 10
    ; doesn't work yet
    :start-at {:ident [:users "alice-user-id"]
               :name "Alice"
               :age 30
               :color "blue"}}])
```

### Reading data (without subscribing)

Get a specific document by giving an ident to `pull`:
```clojure
(go (prn (<! (pull (js/firebase.firestore)
               [:users "alice-user-id"]))))
; => {:ident [:users "alice-user-id"] :name "Alice"}

(go (prn (<! (pull (js/firebase.firestore)
               [:users "nonexistent-user-id"]))))
; => nil
```

Check if a document exists (this will avoid fetching the document's contents
if it does exist):
```clojure
(go (prn (<! (doc-exists? (js/firebase.firestore)
               [:users "alice-user-id"]))))
; => true
```

Get multiple documents with `query`:
```clojure
(go (prn (<! (query (js/firebase.firestore)
               {:ident [:users]
                :where [[:name '> "Alice"]]}))))
; => ({:ident [:users "bob-user-id"]
;      :name "Bob"}
;     {:ident [:users "carol-user-id"]
;      :name "Carol"})
```

### Subcollections

Firestore allows "subcollections", which are described as collections that are attached to a
specific document. For example, a user might have a subcollection for items they've rated:

```clojure
(.. (js/firebase.firestore)
  (collection "users")
  (doc "alice-user-id")
  (collection "items")
  (doc "some-item-id")
  (set #js {:rating "like"}))
```
However, I think it's actually best not to think of subcollections as being nested within a document.
Rather, subcollections are just collections that allow you to prepend arbitrary key-value pairs to the id:
```clojure
(write (js/firebase.firestore)
  {[:items [:users "alice-user-id" "some-item-id"]] {:rating "like"}
   [:items [:users "bob-user-id"   "some-item-id"]] {:rating "dislike"}})

(def db (atom {}))
(let [c (subscribe (js/firebase.firestore)
          [:users])]
  (go-loop []
    (let [changeset (<! c)]
      (swap! db merge-changeset changeset)
      (prn changeset)
      (prn @db)
      (recur))))
; => {[:items [:users "alice-user-id" "some-item-id"]]
;     {:ident [:items [:users "alice-user-id" "some-item-id"]]
;      :rating "like"}
;
;     [:items [:users "bob-user-id" "some-item-id"]]
;     {:ident [:items [:users "bob-user-id" "some-item-id"]]
;      :rating "dislike"}}
;
; => {:items
;     {[:users "alice-user-id" "some-item-id"]
;      {:ident [:items [:users "alice-user-id" "some-item-id"]]
;       :rating "like"}
;
;      [:users "bob-user-id" "some-item-id"]
;      {:ident [:items [:users "bob-user-id" "some-item-id"]]
;       :rating "dislike"}}}

(pull (js/firebase.firestore)
  [:items [:users "bob-user-id" "some-item-id"]])

; Retrieves all items keyed with [:users "bob-user-id"]
(query (js/firebase.firestore)
  [[:items [:users "bob-user-id"]]])
```
If you want to run a query across an entire subcollection
(i.e. not just the portion with a specific prepended key-value pair), you'll
have to specify that you're performing a "collection group" query:
```clojure
(query (js/firebase.firestore)
  [{:collection-group :items
    :where ...}])

; DOESN'T WORK:
(query (js/firebase.firestore)
  [{:ident [:items]
    :where ...}])
```
Modeling subcollections this way keeps your data more normalized, simplifies the logic of `merge-changeset`, and makes
the semantics of subcollections more obvious (as the [docs](https://firebase.google.com/docs/firestore/data-model)
say, "Warning: Deleting a document does not delete its subcollections!").

### Coercions

When reading and writing data, trident.firestore will automatically convert date objects to
and from Firebase's custom `Timestamp` objects:
```clojure
(write (js/firebase.firestore)
  {[:event] {:event-type "click"
             :timestamp (js/Date.)}})

; Equivalent to:
(.. (js/firebase.firestore)
  (collection "event")
  (add (clj->js
         {:event-type "click"
          :timestamp (.fromDate js/firebase.firestore.Timestamp (js/Date.))})))
```
trident.firestore also coerces ref objects for you:
```clojure
(write (js/firebase.firestore)
  {[:users] {:name "Alice"
             :parent [:users "bob-user-id"]}})

; Equivalent to:
(.. (js/firebase.firestore)
  (collection "users")
  (add (clj->js
         {:name "Alice"
          :parent (.. (js/firebase.firestore)
                    (collection "users")
                    (doc "bob-user-id"))})))
```

### Using from Node

You can use this library from Node (e.g. from within a Cloud Function) by simply replacing
calls to `js/firebase.firestore`:
```clojure
(ns some-ns
  (:require
    ["firebase-admin" :refer [firestore]]))

(write (firestore)
  ...)
```

## Status

Not yet supported:
 - Pagination with document arguments (as noted above)
 - Batched writes
 - Pull expressions (I named `pull` with the intention of supporting this
   eventually, though we'll see if it actually happens)
 - Probably other stuff

I'm using this library in production myself but haven't done any QA beyond that. If you
run into any bugs, please let me know. Same goes for the code examples on this page.

## Contact

`#trident` on [Clojurians](https://clojurians.slack.com)

## Self-promotion

If you want to support my work, subscribe to [my newsletter](https://findka.com/subscribe/).

## License

Distributed under the [EPL v2.0](../LICENSE)

Copyright &copy; 2020 [Jacob O'Bryant](https://jacobobryant.com).
