# clojure.java.process-examples

This repository provides a comprehensive suite of usage examples and tests for the `clojure.java.process` library, which offers process execution features similar to [babashka/process](https://github.com/babashka/process).

## What is `clojure.java.process`?

`clojure.java.process` is a Clojure library for spawning and interacting with external processes, capturing output and error streams, passing environment variables, and handling input/output in a functional style.

## Example Usage

Here are some typical patterns (see [test/clojure/java/process_test.clj](test/clojure/java/process_test.clj) for more):

```clojure
;; Run a simple command
(process/run "echo" "Hello world")

;; Capture stderr
(process/run "sh" "-c" "echo error 1>&2")

;; Pass environment variables
(process/run {:env {"FOO" "bar"}} "sh" "-c" "echo $FOO")

;; Handle non-zero exit codes
(process/run "sh" "-c" "exit 42")

;; Send input to a process
(process/run {:in "Input data"} "cat")

;; Run asynchronously
(def proc (process/process "sleep" "1"))
(.waitFor proc)
(.exitValue proc)
```

## Running the Tests

To run the tests using Cognitect's test-runner:

```shell
./script/test
```

## Continuous Integration

This repository includes a [GitHub Actions workflow](.github/workflows/clojure-test.yml) to automatically run the tests on every push and pull request to `main`.

## Contributing

If you discover new features or patterns, please open a PR or issue to help improve this reference for everyone!

---
