# Running build tasks quickly with `trepl.py`

If you run all your build tasks with `clj -m some.namespace ...`, you'll have
to wait for JVM startup and, more importantly, Clojure compilation every time.
As of writing, a simple `(require 'trident.build)` in a fresh repl takes about
15 seconds (sorry!). That's mainly because `trident.build` `require`s a lot of
stuff, but even more minimal build task namespaces will still take several
seconds to load.

I handle this the same way we handle repl startup time in general: open a repl
with dependencies to all the build tasks you need, use that for running build
tasks, and don't close the repl.

Because it would be inconvenient to type `(-main "some-command" "arg1" ...)` all
the time, I have written [`trepl.py`](../bin/trepl.py), a simple script which
can run build tasks from a bash (or whatever) shell over nRepl.

```
$ trepl.py -h
usage: trepl.py [-h] [-p PORT] [-v] {eval,main} ...

Run some code in an nRepl connection.

positional arguments:
  {eval,main}
    eval                Eval some code.
    main                Run an existing function. Requires trident.cli
                        namespace.

optional arguments:
  -h, --help            show this help message and exit
  -p PORT, --port PORT  The nRepl port. If unspecified, will attempt to read
                        port from an .nrepl-port file (either in the current
                        directory or in an ancestor directory).
  -v, --verbose         Print messages to and from nrepl.

$ trepl.py main -h
usage: trepl.py main entry_point [arg1 [arg2 ...]]

positional arguments:
  entry_point  The fully-qualified function name followed by any arguments,
               e.g. `trepl.py main foo.core/bar hello 7`. All arguments will
               be passed as strings.

optional arguments:
  -h, --help   show this help message and exit

The trident.cli namespace must be available. This namespace is used to 1) run
the function in the current directory, 2) disable calls to `System/exit` and
`shutdown-agents`. NOTE: the working directory will be changed for the whole
repl process while this command runs. If that's a problem, you may want to run
a dedicated repl for use with this command.
```

trepl is inspired by [Grenchman](https://github.com/technomancy/grenchman), but:
 - trepl is a single Python file, so it's easier (for me, at least) to develop
   and install.
 - trepl's `main` command has a couple extra features (see the help output
   above).
 - trepl has much fewer features (only what I need).

## Example usage

Start a build repl:
```
# might want to put this in a file
clj -Sdeps '{:deps {trident/build {:mvn/version "RELEASE"}
                    trident/repl  {:mvn/version "RELEASE"}}}' \
    -e "(do (require 'trident.repl)
            (trident.repl/init) ; starts nRepl on port 7888
            (require 'trident.build)) ; just to get compilation out of the way
            " -r
```

Run a build task:
```
# Requires Python 3
./trepl.py -p 7888 main trident.build/-main --help # nice and speedy!
```

You can use an alias for further convenience:
```
alias trepl='/path/to/trepl.py -p 7888 main trident.build/-main'
```
