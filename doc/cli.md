# `trident.cli` Getting Started

In a tools.deps workflow, you normally install build tasks by adding them to
your `deps.edn` file as aliases. But this has a couple downsides:

 - If you want to use the same set of aliases in different projects, you have to
   duplicate them in both `deps.edn` files. You could put the aliases in your
   user `deps.edn` file, but then they won't be available to other developers.

 - Every time you run an alias, you'll have to wait for JVM startup and Clojure
   compilation.

I solve these problems like so:

 - Instead of storing the set of build tasks in `deps.edn`, I put it in a
   library. The library has dependencies to all the build tasks I want, and for
   convenience, it includes a main fn has the build tasks as subcommands.

 - I start an nRepl server with the above library loaded. All build commands are
   executed through this server.

## Example

### The normal way

Execute these commands:
```
mkdir myproject
cd myproject
mkdir -p src/myproject/
cat > src/myproject/core.clj << EOD
(ns myproject.core)

(defn foo [a b]
  (+ a b))
EOD
cat > deps.edn << EOD
{:aliases
 {:new {:extra-deps {seancorfield/clj-new {:mvn/version "0.5.5"}}
        :main-opts ["-m" "clj-new.create"]}
  :jar {:extra-deps {luchiniatwork/cambada {:mvn/version "1.0.0"}}
        :main-opts ["-m" "cambada.jar"]}}}
EOD
time clj -Ajar --help
```

For me, `time clj -Ajar --help` took about 11 seconds.

### The Trident way

Now execute these commands:
```
mkdir myproject2
cd myproject2
mkdir -p src/myproject/
cat > src/myproject/core.clj << EOD
(ns myproject.core)

(defn foo [a b]
  (+ a b))
EOD
echo '{:aliases {:build {:extra-deps {build {:local/root "build"}}}}}' > deps.edn
mkdir build
cat > build/deps.edn << EOD
{:deps {trident/cli {:mvn/version "0.1.5"}
        nrepl {:mvn/version "0.6.0"}
        seancorfield/clj-new {:mvn/version "0.5.5"}
        luchiniatwork/cambada {:mvn/version "1.0.0"}}}
EOD
mkdir -p build/src/myproject
cat > build/src/myproject/build.clj << EOD
(ns myproject.build
  (:require [trident.cli :refer [defmain]]
            [clj-new.create :as create]
            [cambada.jar :as jar]))

(def cli {:subcommands {"new" #'create/-main
                        "jar" #'jar/-main}})
(defmain cli)
EOD
clj -Abuild -e "(do (require 'myproject.build)
                    (require '[nrepl.server :refer [start-server]])
                    (start-server :port 7888))"
```
So we've created a separate `build` project and started a build server. Now we
need an nRepl client. Install [Grenchman](https://leiningen.org/grench.html). If
there aren't binaries available for your system, you can use
[trepl.py](../bin/trepl.py) instead.

After you see the output from `start-server`, run these commands in another terminal:
```
alias build='GRENCH_PORT=7888 grench main trident.cli.util/-main "$PWD" myproject.build/-main'
# Use this instead if you're using trepl.py:
#alias build='trepl.py -p 7888 main trident.cli.util/-main "$PWD" myproject.build/-main'

build --help
time build jar --help
```
Ta-da. That's the essence of my approach to building.

## Details

To reiterate--the main idea here (in addition to using a repl to avoid startup
time) is that we're pushing as much as we can out of `deps.edn` and into a
separate, single library. That way, our build library can be easily shared: we
can use all the infrastructure that's already in place for sharing Clojure code.

A few notes:

 - `trident.cli.util/-main` uses the JNR to change the process's current working
   directory, hence the `"$PWD"`. So the build task will run in whatever
   directory you call it from.

 - `trident.cli.util/-main` also disables calls to `System/exit` and
   `shutdown-agents`.

 - Caveat: `deps.edn` aliases are good for build tasks that need different
   classpaths, e.g. if you want to run tests with different versions of Clojure.
   The `trident.cli` approach doesn't handle that well.

For more details and examples, see
[`trident/cli`](https://cljdoc.org/d/trident/docs/CURRENT/api/trident.cli) and
[`trident/build`](https://cljdoc.org/d/trident/docs/CURRENT/api/trident.build).
