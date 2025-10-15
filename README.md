# clojure.java.process-examples

This repository provides a comprehensive suite of usage examples and tests for the `clojure.java.process` library, a built-in Clojure library (since Clojure 1.12) for spawning and interacting with external processes.

## What is `clojure.java.process`?

`clojure.java.process` is a Clojure library for spawning and interacting with external processes, capturing output and error streams, passing environment variables, and handling input/output in a functional style. It provides a Clojure-friendly wrapper around Java's `ProcessBuilder` and `Process` APIs.

## API Overview

The library provides the following main functions:

- **`exec`** - Execute a command and return captured output (throws on non-zero exit)
- **`start`** - Start a process and return a `Process` object
- **`stdin`** - Get the stdin stream of a process (OutputStream)
- **`stdout`** - Get the stdout stream of a process (InputStream)
- **`stderr`** - Get the stderr stream of a process (InputStream)
- **`exit-ref`** - Get a reference that yields the exit code when dereferenced
- **`to-file`** - Create a redirect to write to a file
- **`from-file`** - Create a redirect to read from a file
- **`io-task`** - Helper for async I/O operations

## Example Usage

Here are comprehensive examples (see [test/clojure/java/process_test.clj](test/clojure/java/process_test.clj) for more):

### Basic Command Execution

```clojure
(require '[clojure.java.process :as process])

;; Run a simple command with exec
(process/exec "echo" "Hello world")
;; => "Hello world\n"

;; Execute with multiple arguments
(process/exec "echo" "foo" "bar" "baz")
;; => "foo bar baz\n"

;; Execute commands that produce no output
(process/exec "true")
;; => ""
```

### Working with Input

```clojure
;; Execute cat with input by writing to stdin
(let [proc (process/start "cat")
      stdin (process/stdin proc)
      stdout (process/stdout proc)]
  (.write stdin (.getBytes "test input\n"))
  (.close stdin)
  (let [output (slurp stdout)]
    (.waitFor proc)
    output))
;; => "test input\n"

;; Pipe input through grep
(let [proc (process/start "grep" "^match$")
      stdin (process/stdin proc)
      stdout (process/stdout proc)]
  (.write stdin (.getBytes "match\nno match\nmatch again"))
  (.close stdin)
  (let [output (slurp stdout)]
    (.waitFor proc)
    output))
;; => "match\n"

;; Transform text with tr
(let [proc (process/start "tr" "a-z" "A-Z")
      stdin (process/stdin proc)
      stdout (process/stdout proc)]
  (.write stdin (.getBytes "hello\n"))
  (.close stdin)
  (let [output (slurp stdout)]
    (.waitFor proc)
    output))
;; => "HELLO\n"
```

### Starting Processes Asynchronously

```clojure
;; Start a process and get a Process object
(def proc (process/start "sleep" "1"))
(.waitFor proc)
(.exitValue proc)
;; => 0

;; Check if process is alive
(.isAlive proc)
;; => false (after waiting)

;; Get process ID
(.pid proc)
;; => 12345 (some number)
```

### Stream Handling

```clojure
;; Write to stdin and read from stdout
(let [proc (process/start "cat")
      stdin (process/stdin proc)
      stdout (process/stdout proc)]
  (.write stdin (.getBytes "test data\n"))
  (.close stdin)
  (let [output (slurp stdout)]
    (.waitFor proc)
    output))
;; => "test data\n"

;; Capture stderr separately
(let [proc (process/start "sh" "-c" "echo error >&2")
      stderr-stream (process/stderr proc)
      error (slurp stderr-stream)]
  (.waitFor proc)
  error)
;; => "error\n"

;; Capture both stdout and stderr
(let [proc (process/start "sh" "-c" "echo out; echo err >&2")
      stdout (slurp (process/stdout proc))
      stderr (slurp (process/stderr proc))]
  (.waitFor proc)
  [stdout stderr])
;; => ["out\n" "err\n"]
```

### Using exit-ref

```clojure
;; Use exit-ref to get exit value
(let [proc (process/start "true")
      exit-val (process/exit-ref proc)]
  @exit-val)
;; => 0

;; Dereferencing waits for process completion
(let [start-time (System/currentTimeMillis)
      proc (process/start "sleep" "0.1")
      exit-val (process/exit-ref proc)
      _ @exit-val
      elapsed (- (System/currentTimeMillis) start-time)]
  elapsed)
;; => 100 ; approximately 100 milliseconds
```

### Environment Variables

```clojure
;; Pass custom environment variables
(process/exec {:env {"MYVAR" "myvalue"}} "sh" "-c" "echo $MYVAR")
;; => "myvalue\n"

;; Pass multiple environment variables
(process/exec {:env {"VAR1" "foo" "VAR2" "bar"}} 
              "sh" "-c" "echo $VAR1 $VAR2")
;; => "foo bar\n"

;; Clear inherited environment
(process/exec {:clear-env true :env {"MYVAR" "test"}} 
              "sh" "-c" "echo $MYVAR")
;; => "test\n"
```

### Working Directory

```clojure
;; Execute in a different directory
(let [temp-dir (System/getProperty "java.io.tmpdir")]
  (process/exec {:dir temp-dir} "pwd"))
;; => "/tmp\n" (or similar)

;; Start process in specific directory
(let [home (System/getProperty "user.home")
      proc (process/start {:dir home} "pwd")
      output (slurp (process/stdout proc))]
  (.waitFor proc)
  output)
;; => "/home/username\n" (or similar)
```

### File I/O and Redirects

```clojure
(require '[clojure.java.io :as io])

;; Redirect output to file
(let [temp-file (io/file "/tmp/output.txt")]
  (let [proc (process/start {:out (process/to-file temp-file)} "echo" "file output")]
    (.waitFor proc)
    (slurp temp-file)))
;; => "file output\n"

;; Redirect stderr to file
(let [temp-file (io/file "/tmp/error.txt")]
  (let [proc (process/start {:err (process/to-file temp-file)} 
                            "sh" "-c" "echo error >&2")]
    (.waitFor proc)
    (slurp temp-file)))
;; => "error\n"

;; Read input from file
(spit "/tmp/input.txt" "file content\n")
(process/exec {:in (process/from-file (io/file "/tmp/input.txt"))} "cat")
;; => "file content\n"

;; Append to file
(spit "/tmp/log.txt" "initial\n")
(let [proc (process/start {:out (process/to-file (io/file "/tmp/log.txt") :append true)} 
                          "echo" "appended")]
  (.waitFor proc)
  (slurp "/tmp/log.txt"))
;; => "initial\nappended\n"
```

### Redirect Options

```clojure
;; Inherit stdout (no capture, prints to parent's stdout)
(let [proc (process/start {:out :inherit} "echo" "inherited")]
  (.waitFor proc))

;; Discard output
(let [proc (process/start {:out :discard} "echo" "discarded")]
  (.waitFor proc))

;; Redirect stderr to stdout
(let [proc (process/start {:err :stdout} "sh" "-c" "echo out; echo err >&2")
      output (slurp (process/stdout proc))]
  (.waitFor proc)
  output)
;; => "out\nerr\n" (both in stdout)
```

### Process Pipelines

```clojure
;; Pipeline: echo | wc
(let [proc1 (process/start "echo" "hello world")
      proc2 (process/start "wc" "-w")
      stdout1 (process/stdout proc1)
      stdin2 (process/stdin proc2)
      stdout2 (process/stdout proc2)]
  ;; Connect processes
  (io/copy stdout1 stdin2)
  (.close stdin2)
  ;; Read result
  (let [output (str/trim (slurp stdout2))]
    (.waitFor proc1)
    (.waitFor proc2)
    output))
;; => "2"

;; Pipeline: cat | grep
(let [proc1 (process/start "cat")
      proc2 (process/start "grep" "match")
      stdin1 (process/stdin proc1)
      stdout1 (process/stdout proc1)
      stdin2 (process/stdin proc2)
      stdout2 (process/stdout proc2)]
  ;; Write to first process
  (.write stdin1 (.getBytes "match this\nno match\nmatch again\n"))
  (.close stdin1)
  ;; Connect processes
  (io/copy stdout1 stdin2)
  (.close stdin2)
  ;; Read final output
  (let [output (slurp stdout2)]
    (.waitFor proc1)
    (.waitFor proc2)
    output))
;; => "match this\nmatch again\n"

;; Three-stage pipeline
(let [proc1 (process/start "seq" "1" "10")
      proc2 (process/start "head" "-n" "5")
      proc3 (process/start "tail" "-n" "2")
      stdout1 (process/stdout proc1)
      stdin2 (process/stdin proc2)
      stdout2 (process/stdout proc2)
      stdin3 (process/stdin proc3)
      stdout3 (process/stdout proc3)]
  (future (io/copy stdout1 stdin2) (.close stdin2))
  (future (io/copy stdout2 stdin3) (.close stdin3))
  (let [output (slurp stdout3)]
    (.waitFor proc1)
    (.waitFor proc2)
    (.waitFor proc3)
    output))
;; => "4\n5\n"
```

### Concurrent Processes

```clojure
;; Run multiple processes concurrently
(let [procs [(process/start "sleep" "0.1")
             (process/start "sleep" "0.1")
             (process/start "sleep" "0.1")]
      start-time (System/currentTimeMillis)]
  (doseq [p procs] (.waitFor p))
  (- (System/currentTimeMillis) start-time))
;; => 100 ; approximately 100ms (not 300ms, they run concurrently)

;; Execute multiple commands in parallel
(let [futures (doall (repeatedly 5 #(future (process/exec "echo" "test"))))]
  (mapv deref futures))
;; => ["test\n" "test\n" "test\n" "test\n" "test\n"]

;; Producer-consumer pattern
(let [producer (process/start "seq" "1" "5")
      consumer (process/start "wc" "-l")
      producer-out (process/stdout producer)
      consumer-in (process/stdin consumer)
      consumer-out (process/stdout consumer)]
  ;; Connect producer to consumer
  (future (io/copy producer-out consumer-in) (.close consumer-in))
  ;; Get result
  (let [output (str/trim (slurp consumer-out))]
    (.waitFor producer)
    (.waitFor consumer)
    output))
;; => "5"
```

### Bidirectional Communication

```clojure
;; Interactive communication with a process
(let [proc (process/start "cat")
      stdin (process/stdin proc)
      stdout (process/stdout proc)
      reader (io/reader stdout)]
  ;; Write and read multiple times
  (.write stdin (.getBytes "first\n"))
  (.flush stdin)
  (let [line1 (.readLine reader)]
    (.write stdin (.getBytes "second\n"))
    (.flush stdin)
    (let [line2 (.readLine reader)]
      (.close stdin)
      (.waitFor proc)
      [line1 line2])))
;; => ["first" "second"]
```

### Process Control

```clojure
;; Destroy a running process
(let [proc (process/start "sleep" "10")]
  (.destroy proc)
  (.waitFor proc)
  (.isAlive proc))
;; => false

;; Force destroy
(let [proc (process/start "sleep" "10")]
  (.destroyForcibly proc)
  (.waitFor proc))

;; Wait with timeout
(let [proc (process/start "sleep" "0.1")
      completed (.waitFor proc 1 java.util.concurrent.TimeUnit/SECONDS)]
  completed)
;; => true

;; Timeout exceeded
(let [proc (process/start "sleep" "10")
      completed (.waitFor proc 100 java.util.concurrent.TimeUnit/MILLISECONDS)]
  (.destroy proc)
  completed)
;; => false
```

### Error Handling

```clojure
;; exec throws on non-zero exit
(try
  (process/exec "sh" "-c" "exit 42")
  (catch RuntimeException e
    "Command failed"))
;; => "Command failed"

;; start returns Process object regardless of exit code
(let [proc (process/start "sh" "-c" "exit 42")]
  (.waitFor proc))
;; => 42

;; Command not found throws exception
(try
  (process/exec "nonexistent-command-xyz")
  (catch Exception e
    "Command not found"))
;; => "Command not found"
```

### Text Processing Examples

```clojure
;; Sort lines
(let [proc (process/start "sort")
      stdin (process/stdin proc)
      stdout (process/stdout proc)]
  (.write stdin (.getBytes "banana\ncherry\napple\n"))
  (.close stdin)
  (let [output (slurp stdout)]
    (.waitFor proc)
    output))
;; => "apple\nbanana\ncherry\n"

;; Count lines
(let [proc (process/start "wc" "-l")
      stdin (process/stdin proc)
      stdout (process/stdout proc)]
  (.write stdin (.getBytes "line1\nline2\nline3\n"))
  (.close stdin)
  (let [output (slurp stdout)]
    (.waitFor proc)
    (str/trim output)))
;; => "3"

;; Extract fields
(let [proc (process/start "cut" "-d" ":" "-f" "2")
      stdin (process/stdin proc)
      stdout (process/stdout proc)]
  (.write stdin (.getBytes "1:2:3\n4:5:6\n"))
  (.close stdin)
  (let [output (slurp stdout)]
    (.waitFor proc)
    output))
;; => "2\n5\n"

;; Transform text with sed
(let [proc (process/start "sed" "s/world/universe/")
      stdin (process/stdin proc)
      stdout (process/stdout proc)]
  (.write stdin (.getBytes "hello world\n"))
  (.close stdin)
  (let [output (slurp stdout)]
    (.waitFor proc)
    output))
;; => "hello universe\n"

;; Process with awk
(let [proc (process/start "awk" "{print $2}")
      stdin (process/stdin proc)
      stdout (process/stdout proc)]
  (.write stdin (.getBytes "hello world\n"))
  (.close stdin)
  (let [output (slurp stdout)]
    (.waitFor proc)
    output))
;; => "world\n"

;; Reverse lines with tac
(let [proc (process/start "tac")
      stdin (process/stdin proc)
      stdout (process/stdout proc)]
  (.write stdin (.getBytes "first\nsecond\nthird\n"))
  (.close stdin)
  (let [output (slurp stdout)]
    (.waitFor proc)
    output))
;; => "third\nsecond\nfirst\n"

;; Remove duplicates
(let [proc (process/start "uniq")
      stdin (process/stdin proc)
      stdout (process/stdout proc)]
  (.write stdin (.getBytes "apple\napple\nbanana\napple\n"))
  (.close stdin)
  (let [output (slurp stdout)]
    (.waitFor proc)
    output))
;; => "apple\nbanana\napple\n"
```

### Advanced Examples

```clojure
;; Base64 encoding
(let [proc (process/start "base64")
      stdin (process/stdin proc)
      stdout (process/stdout proc)]
  (.write stdin (.getBytes "hello\n"))
  (.close stdin)
  (let [output (slurp stdout)]
    (.waitFor proc)
    output))
;; => "aGVsbG8K\n"

;; Base64 decoding
(let [proc (process/start "base64" "-d")
      stdin (process/stdin proc)
      stdout (process/stdout proc)]
  (.write stdin (.getBytes "aGVsbG8K\n"))
  (.close stdin)
  (let [output (slurp stdout)]
    (.waitFor proc)
    output))
;; => "hello\n"

;; Calculate checksums
(let [proc (process/start "sha256sum")
      stdin (process/stdin proc)
      stdout (process/stdout proc)]
  (.write stdin (.getBytes "test\n"))
  (.close stdin)
  (let [output (slurp stdout)]
    (.waitFor proc)
    output))
;; => "hash-value  -\n"

;; Execute with shell features
(process/exec "sh" "-c" "echo $(echo nested)")
;; => "nested\n"

;; Conditional execution
(process/exec "sh" "-c" "true && echo success")
;; => "success\n"

;; Chain commands
(process/exec "sh" "-c" "echo 1; echo 2; echo 3")
;; => "1\n2\n3\n"
```

## Running the Tests

To run the tests using Cognitect's test-runner:

```shell
./script/test
```

Or run specific tests:

```shell
clojure -M:test -n clojure.java.process-test
```

Filter by test name:

```shell
clojure -M:test -v clojure.java.process-test/test-exec-simple-command
```

## Test Coverage

The test suite includes 159 tests covering:

- **Basic exec operations** (30+ tests) - Simple command execution, arguments, output capture
- **Process lifecycle** (25+ tests) - Starting, waiting, exit codes, process control
- **Stream handling** (20+ tests) - stdin, stdout, stderr, bidirectional communication
- **Environment and directory** (15+ tests) - Environment variables, working directory
- **File I/O** (15+ tests) - File redirects, reading from files, writing to files
- **Process pipelines** (20+ tests) - Connecting multiple processes, producer-consumer patterns
- **Concurrent execution** (15+ tests) - Running processes in parallel, concurrent I/O
- **Error handling** (15+ tests) - Non-zero exits, command not found, exceptions
- **Text processing** (20+ tests) - grep, sed, awk, sort, cut, and other utilities
- **Advanced use cases** (20+ tests) - Checksums, encoding, shell features

All tests pass in approximately 11 seconds.

## Continuous Integration

This repository includes a [GitHub Actions workflow](.github/workflows/clojure-test.yml) to automatically run the tests on every push and pull request to `main`.

## Contributing

If you discover new features or patterns, please open a PR or issue to help improve this reference for everyone!

## Resources

- [Clojure Java Process Documentation](https://clojure.github.io/clojure/branch-master/clojure.java.process-api.html)
- [Java ProcessBuilder](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ProcessBuilder.html)
- [Java Process](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Process.html)
- [babashka/process](https://github.com/babashka/process) - Similar library for babashka

---
