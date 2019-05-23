```clojure
[trident "0.0.1"]
```

# Trident

> Because I had to call it something

Trident is my personal experiment in creating a highly-abstracted web
application framework for use with Datomic Cloud.

## Philosophy

We all know that the Clojure community prefers libraries over frameworks. The
difference between a library and a framework isn't well-defined, but I think of
it this way: a framework is just a library with a much larger scope than the
average library. This has several implications:

 - Frameworks have an inversion-of-control feeling: instead of plugging a bunch
   of libraries into your code, you plug your code into the framework. (Some
   people give this as the defining quality of a framework).
 - Frameworks are more likely to not handle all use cases.

The latter point is why we don't like frameworks. The effort required to patch a
framework to accommodate the functionality you want is often higher than the
effort to put all the libraries together yourself. The downside is that now you
have to put all the libraries together yourself, which can be tedious.

It'd be better if we could have the leverage promised by frameworks without
sacrificing the flexibility of libraries. I believe this is entirely achievable;
it just requires careful design. Armin Ronacher wrote a great article about this
kind of design [here]. Essentially, the key is to provide a layered API. The top
layer of the API should be as high-level as possible. When you hit a use case
that the framework doesn't handle, it should be easy to drop down to a lower API
layer and provide whatever customization you need.

To summarize: although there are good reasons for using libraries instead of
frameworks, that doesn't mean we shouldn't put effort into developing good
frameworks. High-quality frameworks can boost the productivity of experienced
Clojure developers and increase Clojure adoption.

Furthermore, Datomic (especially with ions) provides a big opportunity to do
more abstraction. I think we could use a framework that's built specifically for
Datomic, and I'm hoping for Trident to become this framework.

## Status

I wouldn't necessarily recommend actually using this code since it's at a very
early stage, but I'd love to hear about it if you try. If you're interested in
developing applications on top of Datomic, you might enjoy at least reading some
of the code. There isn't any documentation yet (I'm working on that now), but
`trident.web/init!` is a good starting point. There is also [a small
website] which I have written with Trident.

[This article] describes several features which I have since moved into Trident,
including:

 - An authorization system that allows the frontend to send arbitrary
   transactions
 - DataScript as the frontend memory store, plus some light tools for syncing
   datoms
 - Custom transaction functions without having to deploy them first

In addition, I've organized Trident in a way that makes it highly modular
without sacrificing convenience. All the code is stored in a single `src`
folder, and individual projects (along with their dependencies) are defined in
the `trident.edn` file. `jobryant.build` is used to take slices of the codebase and
package them into jars, along with a couple other tasks.

<!-- todo update, preferably automatically 
The available artifacts (all with the same version) include:

 - `jobryant/util`
 - `jobryant/firebase`
 - `jobryant/views`
 - `jobryant/ion`
 - `jobryant/datascript`
 - `jobryant/datomic`
 - `jobryant/datomic-cloud`
 - `jobryant/trident`
 - `jobryant/trident-front`
 - `jobryant/trident-dev`

The code of each artifact consists of:

1. `src/jobryant/<name>*`, e.g. `jobryant/util` includes
   `src/jobryant/util.cljc` and `src/jobryant/util/`.
2. The code from any artifacts listed under `:local-deps` in `trident.edn`. For
   example, the line `trident {:local-deps [util datomic-cloud firebase ion]`
   means that `jobryant/trident` also includes `jobryant/util`,
   `jobryant/datomic-cloud`, etc.

-->

## Progress

Rather than trying to cover a bunch of use cases up front, I'm taking the
approach of:

 - Build real applications
 - Along the way, move as much code as possible from the apps' codebases into
   Trident

Currently the only application I've built with Trident is [FlexBudget], so
Trident is still pretty contrived for that use case.

Future work includes:

 - Write more documentation: docstrings, high-level information about Trident's architecture,
   tutorials, etc.
 - Integrate existing libraries/frameworks as needed. For example, I haven't
   used Fulcro at all, but I'd like to explore it.
 - Add facilities for real-time/subscribable queries. If/when reactive datalog
   becomes available, I'd like to use that; in the mean time, I'm planning to do
   something more manual.
 - Tools for mobile apps (I'm not very experienced with mobile development, but
   I'll need to start writing some mobile apps soon)

## License

Distributed under the [EPL v2.0]

Copyright &copy; 2019 [Jacob O'Bryant].

[EPL v2.0]: https:/github.com/jobryant/trident/blob/master/LICENSE
[Jacob O'Bryant]: https://jacobobryant.com
[FlexBudget]: https://notjust.us
[here]: http://lucumr.pocoo.org/2013/2/13/moar-classes/
[planck]: https://github.com/planck-repl/planck
[This article]: https://jacobobryant.com/post/2019/ion/
[a small website]: https://github.com/jacobobryant/bud
