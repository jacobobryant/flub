# Flub

> During a big rock show, you can flub a few things and nobody will hear it,
> because it gets buried under everything else.
>
> — Jonny Lang

This is a collection of utility libraries containing code that I pull out of
various projects I work on. (I used to call this "Trident," see
[History](#history)).

[flub.edn](flub.edn) defines the dependencies for all the libs, and
[task](task) generates the libs' individual deps.edn files.

## Usage

These are only published on git. If you want to use something, put this in your
deps.edn:

```clojure
{:deps
 {:git/url "https://github.com/jacobobryant/flub",
  :deps/root "core" ; change this value to specify which library you want
  :tag "HEAD"}}
```

And then run `clj -X:deps git-resolve-deps` to fetch the latest commit hash. If
you're using Leiningen, check out
[lein-git-down](https://github.com/reifyhealth/lein-git-down).

In many cases it might be best to just copy anything you want into your own
project/lib.

## Contents

The libraries are generally separated based on their dependencies, so you
should be able to depend on whatever you need without pulling in too much extra
baggage. There are no docstrings, so you'll have to read the source.

### flub.core

Helper fns with *almost* no dependencies (the only exception is
`org.clojure/tools.namespace`). Notable functions:

 - `start-system` and `refresh`, my 15-line alternative to
   Component/Mount/Integrant (probably not for everyone, but it does everything
   I need it to). You store your system in a single map with flat, namespaced
   keys, then pass it through "component" functions which modify the system
   map. It's kind of like a Ring request going through middleware functions.
   Use it like so:

```clojure
(ns yourapp.core
  (:require [flub.core :as flub]))

(def your-components [...])

(defn start [first-start]
  (flub/start-system
    {:flub/first-start first-start
     :flub/after-refresh `after-refresh}
    your-components)
  (println "System started."))

(defn -main []
  (start true))

(defn after-refresh []
  (start false))

(comment
  (prn (keys @flub/system))
  (flub/refresh) ; I map this to <leader>R in vim
  )
```

 - `select-ns`, `select-ns-as`, `prepend-ns`. These help you work with flat,
   namespaced keys. They go well with `start-system`.

### flub.components

A few components for use with `flub.core/start-system`. There are currently components
for nREPL, Reitit and Jetty.

### flub.crux

Helper fns for the world's niftiest database. I really should write docstrings
for these ones.

I recommend aliasing this namespace as `flux`.

### flub.middleware

Some Ring middleware, my personal fav being `wrap-flat-keys` which lets you do
stuff like this:

```clojure
(defn handler [{:keys [session/foo params/bar]}]
  {:status 200
   :headers/content-type "text/html; charset=utf-8"
   :cookies/baz {...}})
```

### flub.views

Rum components etc. Mainly there's a `base` component which fills in a lot
of stuff in `<head>` for you.

### flub.extra

Because I couldn't bring myself to call it `misc`. These functions can't go in
`flub.core` since they have dependencies, but they don't fit anywhere else
either.

### flub.malli

A single function `assert` which throws an exception (with human-readable
explanation) if the given value doesn't conform to the given Malli schema.

This would make a lot of sense to throw in `flub.extra`; however, I wanted to
use it from `flub.crux`, and I also wanted to make it available without
depending on Crux.

## Releasing

In case you want to use a similar setup for your own collection of libraries,
Flub requires two commits to release. In the first commit, you must include all
the new code and external dependencies. (Run `./task sync` to update the
external dependencies for each project, after you define them in `flub.edn`\*).
After you commit those changes, run `./task sync` again, then commit and push.
This will update the commit hashes for all the internal dependencies (i.e. Flub
libraries that depend on each other).

\* But don't actually call it flub.edn, come up with your own name.

## History

This repository used to be called Trident. It contained a bunch of code that I
stopped using, and it had an overly complicated monorepo setup that caused me
to stop updating any of the libraries since it took a while to remember how
(which compounded the first problem). I've given Trident a new start in life as
Flub (now 43% faster to type!). You can [browse the last Trident
commit](https://github.com/jacobobryant/trident/tree/9f2fec6f3d9875b0b533725aa7ca3c0c5e225110)
if you want to see the old code.

There is one part that's possibly worth moving to Flub:
[trident.firestore](https://github.com/jacobobryant/trident/blob/9f2fec6f3d9875b0b533725aa7ca3c0c5e225110/doc/firestore.md).
It's a CLJS wrapper for Firestore that uses core.async. I used it to write
[Mystery Cows](https://github.com/jacobobryant/mystery-cows). I also used it in
[my startup](https://findka.com) before I switched to
[Biff](https://findka.com/biff). Since I no longer use Firebase, the code will
probably languish in obscurity unless someone else wants to take over.

## License

Distributed under the [MIT License](LICENSE), which [you might also
want](https://twitter.com/deobald/status/1367541123619557378) to start using
for your Clojure libraries.

Copyright &copy; 2021 [Jacob O'Bryant](https://twitter.com/obryant666).
