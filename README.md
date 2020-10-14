```clojure
trident/<artifact> {:mvn/version "0.3.2"}
```

# Trident

> Because I had to call it something

Trident is where I put all the code that I abstract out of various projects I
work on.

## Contents

I keep the code in a single `src` dir, but I provide multiple artifacts. Any
code in `src/trident/<foo>` is available in the `trident/<foo>` artifact. For
example, `{trident/cli {:mvn/version "<version>"}}` will give you access to the
`trident.cli` namespace. All artifacts use the same version.

 - [`trident/repl`](https://cljdoc.org/d/trident/docs/CURRENT/api/trident.repl). A handful of convenience functions for use at the repl.
 - [`trident/util`](https://cljdoc.org/d/trident/docs/CURRENT/api/trident.util). A collection of utility functions & macros.
 - [`trident/cli`](https://cljdoc.org/d/trident/docs/CURRENT/api/trident.cli). Makes it easy to define and reuse CLIs for tools.deps-based build tasks.
 - [`trident/jwt`](https://cljdoc.org/d/trident/docs/CURRENT/api/trident.jwt). (En|de)code JWTs.
 - [`trident/ion-dev`](https://cljdoc.org/d/trident/docs/CURRENT/api/trident.ion-dev). Stuff for ion development.
 - [`trident/build`](https://cljdoc.org/d/trident/docs/CURRENT/api/trident.build). A collection of build tasks made with `trident/cli`.
 - [`trident/datomic`](https://cljdoc.org/d/trident/docs/CURRENT/api/trident.datomic). Janky file-based persistence for Datomic Free.
 - [`trident/datomic-cloud`](https://cljdoc.org/d/trident/docs/CURRENT/api/trident.datomic-cloud). Tools for Datomic Cloud.
 - [`trident/ring`](https://cljdoc.org/d/trident/docs/CURRENT/api/trident.ring). Some Ring middleware.
 - [`trident/firebase`](https://cljdoc.org/d/trident/docs/CURRENT/api/trident.firebase). Functions for authenticating Firebase user tokens.
 - [`trident/firestore`](doc/firestore.md). A wrapper for Firestore.
 - [`trident/ion`](https://cljdoc.org/d/trident/docs/CURRENT/api/trident.ion). Utilities for working with Datomic Ions
 - [`trident/web`](https://cljdoc.org/d/trident/docs/CURRENT/api/trident.web). Highly contrived web framework.
 - [`trident/staticweb`](https://cljdoc.org/d/trident/docs/CURRENT/api/trident.staticweb). Tools for making static websites
 - [`trident/datascript`](https://cljdoc.org/d/trident/docs/CURRENT/api/trident.datascript). Frontend tools for syncing Datascript with Datomic.
 - [`trident/views`](https://cljdoc.org/d/trident/docs/CURRENT/api/trident.views). Some Reagent components and stuff.
 - [`trident/cljs-http`](https://cljdoc.org/d/trident/docs/CURRENT/api/trident.cljs-http). Slight additions to `cljs-http`


## License

Distributed under the [EPL v2.0](LICENSE)

Copyright &copy; 2020 [Jacob O'Bryant](https://jacobobryant.com).

[documentation on cljdoc]: https://cljdoc.org/d/trident/docs/CURRENT/doc/readme
[trepl.py]: https://cljdoc.org/d/trident/docs/CURRENT/doc/running-build-tasks-quickly-with-trepl-py-
