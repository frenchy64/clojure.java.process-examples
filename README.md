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

## Comparison with babashka.process

Both `clojure.java.process` (built into Clojure 1.12+) and `babashka.process` provide ways to shell out to external processes. Here's a comprehensive comparison to help you migrate between them or choose the right one for your use case.

### Summary Table

| Feature | clojure.java.process | babashka.process |
|---------|---------------------|------------------|
| **Availability** | Built into Clojure 1.12+ | Separate library, built into Babashka |
| **Basic execution** | `exec` (throws on error) | `shell` (throws on error), `sh` (doesn't throw) |
| **Async execution** | `start` returns Process | `process` returns process map |
| **Output capture** | Manual with streams or via exec | `:out :string`, `:out :bytes` |
| **Input passing** | Manual stream writing | `:in "string"`, `:in (io/file ...)` |
| **Error handling** | Throws RuntimeException | `check` function, `:continue` option |
| **Piping** | Manual with `io/copy` | Built-in with `->`, pipeline support |
| **Working directory** | `:dir` option | `:dir` option |
| **Environment vars** | `:env` map, `:clear-env` | `:env`, `:extra-env` |
| **Stream access** | `stdin`, `stdout`, `stderr` functions | Direct map access `:in`, `:out`, `:err` |
| **Tokenization** | Manual | Automatic for first string arg |
| **Process control** | Java Process methods | `destroy`, `destroy-tree`, `alive?` |

### Feature-by-Feature Comparison with Examples

#### 1. Basic Command Execution

**clojure.java.process:**
```clojure
(require '[clojure.java.process :as cjp])

;; Throws on non-zero exit
(cjp/exec "echo" "Hello")
;; => "Hello\n"
```

**babashka.process:**
```clojure
(require '[babashka.process :as bp])

;; Throws on non-zero exit, captures output
(-> (bp/shell {:out :string} "echo Hello") :out)
;; => "Hello\n"

;; Doesn't throw by default, like clojure.java.shell/sh
(-> (bp/sh "echo Hello") :out)
;; => "Hello\n"
```

#### 2. Async Process Execution

**clojure.java.process:**
```clojure
(require '[clojure.java.process :as cjp])

;; Start process and wait later
(let [proc (cjp/start "sleep" "0.1")]
  (.waitFor proc)
  (.exitValue proc))
;; => 0
```

**babashka.process:**
```clojure
(require '[babashka.process :as bp])

;; Process returns a map with Process object
(let [proc (bp/process "sleep" "0.1")]
  @proc ; deref waits for completion
  (:exit proc))
;; => 0
```

#### 3. Capturing Output

**clojure.java.process:**
```clojure
(require '[clojure.java.process :as cjp])

;; Using exec (easiest)
(cjp/exec "echo" "output")
;; => "output\n"

;; Using start with manual stream handling
(let [proc (cjp/start "echo" "output")
      output (slurp (cjp/stdout proc))]
  (.waitFor proc)
  output)
;; => "output\n"
```

**babashka.process:**
```clojure
(require '[babashka.process :as bp])

;; Capture as string
(-> (bp/process {:out :string} "echo" "output") deref :out)
;; => "output\n"

;; Capture as bytes
(-> (bp/process {:out :bytes} "echo" "output") deref :out)
;; => #object["[B" ... ]
```

#### 4. Providing Input

**clojure.java.process:**
```clojure
(require '[clojure.java.process :as cjp])

;; Manual stdin writing
(let [proc (cjp/start "cat")
      stdin (cjp/stdin proc)
      stdout (cjp/stdout proc)]
  (.write stdin (.getBytes "input\n"))
  (.close stdin)
  (let [output (slurp stdout)]
    (.waitFor proc)
    output))
;; => "input\n"
```

**babashka.process:**
```clojure
(require '[babashka.process :as bp])

;; Direct string input
(-> (bp/process {:in "input\n" :out :string} "cat") deref :out)
;; => "input\n"
```

#### 5. Handling Non-Zero Exit Codes

**clojure.java.process:**
```clojure
(require '[clojure.java.process :as cjp])

;; exec throws on non-zero
(try
  (cjp/exec "sh" "-c" "exit 1")
  (catch Exception e
    "Failed"))
;; => "Failed"

;; start doesn't throw, check manually
(let [proc (cjp/start "sh" "-c" "exit 1")]
  (.waitFor proc))
;; => 1
```

**babashka.process:**
```clojure
(require '[babashka.process :as bp])

;; shell throws by default
(try
  (bp/shell "sh" "-c" "exit 1")
  (catch Exception e
    "Failed"))
;; => "Failed"

;; Use :continue to prevent throwing
(-> (bp/shell {:continue true :out :string} "sh" "-c" "exit 1") :exit)
;; => 1
```

#### 6. Environment Variables

**clojure.java.process:**
```clojure
(require '[clojure.java.process :as cjp])

;; Set environment variables
(cjp/exec {:env {"FOO" "bar"}} "sh" "-c" "echo $FOO")
;; => "bar\n"

;; Clear inherited environment
(cjp/exec {:clear-env true :env {"FOO" "bar"}} "sh" "-c" "echo $FOO")
;; => "bar\n"
```

**babashka.process:**
```clojure
(require '[babashka.process :as bp])

;; Add to existing environment
(-> (bp/process {:extra-env {"FOO" "bar"} :out :string} 
                "sh" "-c" "echo $FOO") 
    deref :out)
;; => "bar\n"

;; Replace environment
(-> (bp/process {:env {"FOO" "bar"} :out :string} 
                "sh" "-c" "echo $FOO") 
    deref :out)
;; => "bar\n"
```

#### 7. Working Directory

**clojure.java.process:**
```clojure
(require '[clojure.java.process :as cjp])

;; Execute in different directory
(cjp/exec {:dir "/tmp"} "pwd")
;; => "/tmp\n"
```

**babashka.process:**
```clojure
(require '[babashka.process :as bp])

;; Execute in different directory
(-> (bp/process {:dir "/tmp" :out :string} "pwd") deref :out)
;; => "/tmp\n"
```

#### 8. Process Piping

**clojure.java.process:**
```clojure
(require '[clojure.java.process :as cjp]
         '[clojure.java.io :as io])

;; Manual piping
(let [proc1 (cjp/start "echo" "hello")
      proc2 (cjp/start "tr" "a-z" "A-Z")
      stdout1 (cjp/stdout proc1)
      stdin2 (cjp/stdin proc2)
      stdout2 (cjp/stdout proc2)]
  (io/copy stdout1 stdin2)
  (.close stdin2)
  (let [output (slurp stdout2)]
    (.waitFor proc1)
    (.waitFor proc2)
    output))
;; => "HELLO\n"
```

**babashka.process:**
```clojure
(require '[babashka.process :as bp])

;; Built-in pipeline support
(-> (bp/process "echo" "hello")
    (bp/process "tr" "a-z" "A-Z")
    (bp/process {:out :string} "cat")
    deref :out)
;; => "HELLO\n"
```

#### 9. Redirecting stderr

**clojure.java.process:**
```clojure
(require '[clojure.java.process :as cjp])

;; Redirect stderr to stdout
(let [proc (cjp/start {:err :stdout} "sh" "-c" "echo out; echo err >&2")
      output (slurp (cjp/stdout proc))]
  (.waitFor proc)
  output)
;; => "out\nerr\n"
```

**babashka.process:**
```clojure
(require '[babashka.process :as bp])

;; Redirect stderr to stdout
(-> (bp/process {:err :out :out :string} "sh" "-c" "echo out; echo err >&2")
    deref :out)
;; => "out\nerr\n"
```

#### 10. File I/O

**clojure.java.process:**
```clojure
(require '[clojure.java.process :as cjp]
         '[clojure.java.io :as io])

;; Read from file
(spit "/tmp/test-input.txt" "content\n")
(cjp/exec {:in (cjp/from-file (io/file "/tmp/test-input.txt"))} "cat")
;; => "content\n"

;; Write to file
(let [proc (cjp/start {:out (cjp/to-file (io/file "/tmp/test-output.txt"))} 
                      "echo" "output")]
  (.waitFor proc)
  (slurp "/tmp/test-output.txt"))
;; => "output\n"
```

**babashka.process:**
```clojure
(require '[babashka.process :as bp]
         '[clojure.java.io :as io])

;; Read from file
(spit "/tmp/test-input2.txt" "content\n")
(-> (bp/process {:in (io/file "/tmp/test-input2.txt") :out :string} "cat")
    deref :out)
;; => "content\n"

;; Write to file
(-> (bp/process {:out (io/file "/tmp/test-output2.txt")} "echo" "output")
    deref)
(slurp "/tmp/test-output2.txt")
;; => "output\n"
```

#### 11. Checking if Process is Alive

**clojure.java.process:**
```clojure
(require '[clojure.java.process :as cjp])

;; Check if alive
(let [proc (cjp/start "sleep" "10")]
  (let [alive (.isAlive proc)]
    (.destroy proc)
    alive))
;; => true
```

**babashka.process:**
```clojure
(require '[babashka.process :as bp])

;; Check if alive
(let [proc (bp/process "sleep" "10")]
  (let [alive (bp/alive? proc)]
    (bp/destroy proc)
    alive))
;; => true
```

#### 12. Tokenization

**clojure.java.process:**
```clojure
(require '[clojure.java.process :as cjp])

;; No automatic tokenization - provide args separately
(cjp/exec "ls" "-la")
;; => "total ...\n..."
```

**babashka.process:**
```clojure
(require '[babashka.process :as bp])

;; Automatic tokenization of first argument
(-> (bp/shell {:out :string} "ls -la") :out)
;; => "total ...\n..."
```

### Migration Guide

#### From clojure.java.process to babashka.process

```clojure
;; Before (clojure.java.process)
(require '[clojure.java.process :as cjp])
(cjp/exec "echo" "hello")

;; After (babashka.process)
(require '[babashka.process :as bp])
(-> (bp/shell {:out :string} "echo hello") :out)
;; or
(-> (bp/sh "echo" "hello") :out)
```

#### From babashka.process to clojure.java.process

```clojure
;; Before (babashka.process)
(require '[babashka.process :as bp])
(-> (bp/shell {:out :string} "echo hello") :out)

;; After (clojure.java.process)
(require '[clojure.java.process :as cjp])
(cjp/exec "echo" "hello")
```

### Key Differences Summary

1. **Return Values**: `clojure.java.process/exec` returns a string directly, while `babashka.process` functions return process maps that need to be dereferenced and accessed via `:out`

2. **Input Handling**: `clojure.java.process` requires manual stream manipulation for input, while `babashka.process` accepts `:in` as a string, file, or stream

3. **Piping**: `clojure.java.process` requires manual `io/copy` between processes, while `babashka.process` has built-in pipeline support

4. **Error Handling**: Both throw on non-zero exit by default (`exec`/`shell`), but `babashka.process` has more flexible options with `:continue` and `check`

5. **Convenience**: `babashka.process` provides more convenience features like automatic tokenization and simpler output capture

6. **Portability**: `clojure.java.process` is built into Clojure 1.12+, while `babashka.process` requires a dependency (but is built into Babashka)

Choose `clojure.java.process` when:
- You're using Clojure 1.12+ and want zero dependencies
- You need fine-grained control over process streams
- You're building a library that should minimize dependencies

Choose `babashka.process` when:
- You want more convenience features and simpler API
- You're using Babashka (where it's built-in)
- You need built-in piping support
- You want automatic string tokenization

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
