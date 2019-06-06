```clojure
{trident/<artifact> {:mvn/version "0.1.3"}}
```

# Trident

> Because I had to call it something

Trident is my personal experiment in creating a highly-abstracted web
application framework for use with Datomic Cloud. But it's also just a
collection of libraries, containing anything I happen to abstract out of
projects I'm working on.

My top-level goal with Trident is to maximize code reuse (especially my own, but
hopefully for others as well). This has two parts:

 - There should be high-level interfaces that give you lots of leverage (like
   frameworks).

 - When the high-level interfaces don't cut it, it should be easy to use only
   the parts you want, customizing behavior as needed (like libraries).

## Contents

I keep the code in a single `src` dir, but I provide multiple artifacts. Any
code in `src/trident/<foo>` is available in the `trident/<foo>` artifact. For
example, `{trident/cli {:mvn/version "<version>"}}` will give you access to the
`trident.cli` namespace. All artifacts use the same version.

I use the `trident/docs` artifact for [documentation on cljdoc]. You can browse
the namespaces there to see what's available, but briefly:

 - `trident.build` is a collection of deps.edn-based build tasks. I've provided
   a consistent interface over tasks for generating `pom.xml`; packaging,
   installing and deploying jars; and ingesting code into a locally running
   instance of cljdoc, and I've also provided tasks for working with monolithic
   projects (like this one).

   Also see [trepl.py], a script that makes running build tasks fast.

 - `trident.cli` makes it easy to define and reuse command line interfaces for
   deps.edn-based build tasks. (I use it in `trident.build`).

 - `trident.util` is a collection of utility functions & macros.

 - `trident.repl` is a handful of convenience functions for use at the repl.

There are other libraries in there (including libraries for working with
Datomic), but I'll make those visible after I've written documentation for them.

## Progress

While I hope Trident will be useful for others, I'm primarily focusing on my own
use cases because 1) there are things I want to build, 2) developing Trident as
I build real things will help to make sure it's actually useful. With that out
of the way, if you're interested in anything Trident provides, I'd love to chat.

## Contact

 - [email](mailto:foo@jacobobryant.com)
 - [twitter](https://twitter.com/obryant666)
 - `#trident` on [Clojurians](https://clojurians.slack.com)

## License

Distributed under the [EPL v2.0](LICENSE)

Copyright &copy; 2019 [Jacob O'Bryant](https://jacobobryant.com).

[documentation on cljdoc]: https://cljdoc.org/d/trident/docs/CURRENT/doc/readme
[trepl.py]: https://cljdoc.org/d/trident/docs/CURRENT/doc/running-build-tasks-quickly-with-trepl-py-
