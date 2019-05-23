function repl {
  clj -A:dev -e "(do (require 'trident.repl) (init))" -r
}
