```clojure
trident/<artifact> {:mvn/version "0.2.2"}
```

# Trident

> Because I had to call it something

Trident is where I put all the code that I abstract out of various projects I
work on. My top-level goal with Trident is to maximize code reuse (especially my
own, but hopefully for others as well). This has two parts:

## Contents

I keep the code in a single `src` dir, but I provide multiple artifacts. Any
code in `src/trident/<foo>` is available in the `trident/<foo>` artifact. For
example, `{trident/cli {:mvn/version "<version>"}}` will give you access to the
`trident.cli` namespace. All artifacts use the same version.

 - [`trident/repl`](https://cljdoc.org/d/trident/docs/CURRENT/api/trident.repl). A handful of convenience functions for use at the repl.
 - [`trident/util`](https://cljdoc.org/d/trident/docs/CURRENT/api/trident.util). A collection of utility functions & macros.
 - [`trident/cli`](https://cljdoc.org/d/trident/docs/CURRENT/api/trident.cli) (includes `util`). Makes it easy to define and reuse CLIs for tools.deps-based build tasks.
 - [`trident/ion-dev`](https://cljdoc.org/d/trident/docs/CURRENT/api/trident.ion-dev) (includes `cli`). Stuff for ion development.
 - [`trident/build`](https://cljdoc.org/d/trident/docs/CURRENT/api/trident.build) (includes `util`, `cli`). A collection of build tasks made with `trident/cli`.
 - [`trident/datomic`](https://cljdoc.org/d/trident/docs/CURRENT/api/trident.datomic) (includes `util`). Janky file-based persistence for Datomic Free.
 - [`trident/datomic-cloud`](https://cljdoc.org/d/trident/docs/CURRENT/api/trident.datomic-cloud) (includes `util`). Tools for Datomic Cloud.
 - [`trident/ring`](https://cljdoc.org/d/trident/docs/CURRENT/api/trident.ring). Some Ring middleware.
 - [`trident/firebase`](https://cljdoc.org/d/trident/docs/CURRENT/api/trident.firebase). Functions for authenticating Firebase user tokens.
 - [`trident/firestore`](doc/firestore.md). A wrapper for Firestore.
 - [`trident/ion`](https://cljdoc.org/d/trident/docs/CURRENT/api/trident.ion) (includes `util`). Utilities for working with Datomic Ions
 - [`trident/web`](https://cljdoc.org/d/trident/docs/CURRENT/api/trident.web) (includes `util`, `datomic-cloud`, `firebase`, `ion`, `ring`). Highly contrived web framework.
 - [`trident/staticweb`](https://cljdoc.org/d/trident/docs/CURRENT/api/trident.staticweb). Tools for making static websites
 - [`trident/datascript`](https://cljdoc.org/d/trident/docs/CURRENT/api/trident.datascript) (includes `util`). Frontend tools for syncing Datascript with Datomic.
 - [`trident/views`](https://cljdoc.org/d/trident/docs/CURRENT/api/trident.views) (includes `util`). Some Reagent components and stuff.
 - [`trident/cljs-http`](https://cljdoc.org/d/trident/docs/CURRENT/api/trident.cljs-http). Slight additions to `cljs-http`
 - `trident/frontend` (includes `util`, `datascript`, `views`, `cljs-http`). Just a bundle of other artifacts.

Also see [`trident.cli` Getting Started](doc/cli.md).

## Projects using Trident

 - [Findka](https://findka.com), a cross-domain recommender system.
 - [Lagukan](https://lagukan.com), a music recommender system.
 - [FlexBudget](https://github.com/jacobobryant/flexbudget), a [flexible budgeting app](https://notjust.us).

(All mine so far).

## Contact

`#trident` on [Clojurians](https://clojurians.slack.com)

## Self-promotion

If you want to support my work, subscribe to [my newsletter](https://jacobobryant.substack.com).

## License

Distributed under the [EPL v2.0](LICENSE)

Copyright &copy; 2020 [Jacob O'Bryant](https://jacobobryant.com).

[documentation on cljdoc]: https://cljdoc.org/d/trident/docs/CURRENT/doc/readme
[trepl.py]: https://cljdoc.org/d/trident/docs/CURRENT/doc/running-build-tasks-quickly-with-trepl-py-
