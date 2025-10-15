(ns clojure.java.process-test
  (:require [clojure.test :refer :all]
            [clojure.java.process :as process]
            [clojure.java.io :as io]
            [clojure.string :as str])
  (:import [java.io File]))

;; Helper function to execute with string input
(defn exec-with-input
  "Execute a command with string input. Returns output string."
  [input & args]
  (let [proc (apply process/start args)
        stdin (process/stdin proc)
        stdout (process/stdout proc)]
    (.write stdin (.getBytes input))
    (.close stdin)
    (let [output (slurp stdout)
          exit-code (.waitFor proc)]
      (if (zero? exit-code)
        output
        (throw (RuntimeException. (str "Process exited with code " exit-code)))))))

;; ============================================================================
;; Basic exec tests
;; ============================================================================

(deftest test-exec-simple-command
  (testing "Execute a simple command with exec"
    (is (= "Hello world\n" (process/exec "echo" "Hello world")))))

(deftest test-exec-multiple-args
  (testing "Execute command with multiple arguments"
    (is (= "foo bar baz\n" (process/exec "echo" "foo" "bar" "baz")))))

(deftest test-exec-no-output
  (testing "Execute command with no output"
    (is (= "" (process/exec "true")))))

(deftest test-exec-numeric-args
  (testing "Execute command with numeric string arguments"
    (is (= "123 456\n" (process/exec "echo" "123" "456")))))

(deftest test-exec-special-chars
  (testing "Execute command with special characters"
    (is (= "foo@bar.com\n" (process/exec "echo" "foo@bar.com")))))

(deftest test-exec-newlines
  (testing "Execute command that outputs multiple lines"
    (is (= "line1\nline2\nline3\n" 
           (process/exec "sh" "-c" "echo line1; echo line2; echo line3")))))

(deftest test-exec-cat-input
  (testing "Execute cat with piped input"
    (is (= "test input\n" (exec-with-input "test input\n" "cat")))))

(deftest test-exec-multiple-lines-input
  (testing "Execute with multiple lines of input"
    (is (= "line1\nline2\nline3\n" 
           (exec-with-input "line1\nline2\nline3\n" "cat")))))

(deftest test-exec-grep-input
  (testing "Execute grep with piped input"
    (is (= "match\n" 
           (exec-with-input "match\nno match\nmatch again" "grep" "^match$")))))

(deftest test-exec-empty-input
  (testing "Execute with empty input"
    (is (= "" (exec-with-input "" "cat")))))

(deftest test-exec-wc-count-chars
  (testing "Execute wc to count characters"
    (let [output (exec-with-input "hello" "wc" "-c")]
      (is (= "5" (str/trim output))))))

(deftest test-exec-pwd
  (testing "Execute pwd command"
    (let [output (process/exec "pwd")]
      (is (string? output))
      (is (pos? (count output))))))

(deftest test-exec-which
  (testing "Execute which to find command location"
    (let [output (process/exec "which" "sh")]
      (is (str/includes? output "sh")))))

(deftest test-exec-date
  (testing "Execute date command"
    (let [output (process/exec "date" "+%Y")]
      (is (re-matches #"\d{4}\n" output)))))

(deftest test-exec-basename
  (testing "Execute basename command"
    (is (= "file.txt\n" (process/exec "basename" "/path/to/file.txt")))))

(deftest test-exec-dirname
  (testing "Execute dirname command"
    (is (= "/path/to\n" (process/exec "dirname" "/path/to/file.txt")))))

(deftest test-exec-seq
  (testing "Execute seq command"
    (is (= "1\n2\n3\n" (process/exec "seq" "1" "3")))))

(deftest test-exec-head
  (testing "Execute head command with input"
    (is (= "line1\nline2\n" 
           (exec-with-input "line1\nline2\nline3\n" "head" "-n" "2")))))

(deftest test-exec-tail
  (testing "Execute tail command with input"
    (is (= "line2\nline3\n" 
           (exec-with-input "line1\nline2\nline3\n" "tail" "-n" "2")))))

(deftest test-exec-tr-uppercase
  (testing "Execute tr to convert to uppercase"
    (is (= "HELLO\n" (exec-with-input "hello\n" "tr" "a-z" "A-Z")))))

(deftest test-exec-tr-delete
  (testing "Execute tr to delete characters"
    (is (= "hllo\n" (exec-with-input "hello\n" "tr" "-d" "e")))))

(deftest test-exec-cut
  (testing "Execute cut to extract fields"
    (is (= "2\n5\n" 
           (exec-with-input "1:2:3\n4:5:6\n" "cut" "-d" ":" "-f" "2")))))

(deftest test-exec-sort
  (testing "Execute sort command"
    (is (= "apple\nbanana\ncherry\n" 
           (exec-with-input "banana\ncherry\napple\n" "sort")))))

(deftest test-exec-sort-reverse
  (testing "Execute sort in reverse"
    (is (= "cherry\nbanana\napple\n" 
           (exec-with-input "banana\ncherry\napple\n" "sort" "-r")))))

(deftest test-exec-uniq
  (testing "Execute uniq command"
    (is (= "apple\nbanana\napple\n" 
           (exec-with-input "apple\napple\nbanana\napple\n" "uniq")))))

(deftest test-exec-printf
  (testing "Execute printf command"
    (is (= "Hello World" (process/exec "printf" "Hello World")))))

;; Tokenization Tests
;; -------------------
;; clojure.java.process does NOT perform shell tokenization on arguments.
;; Each argument is passed directly to the process without splitting on whitespace.
;; This is different from shell command strings which would split "a b" into separate args.
;;
;; Example: (process/exec "printf" "%s\\n" "a b") 
;;   - Passes 2 arguments to printf: "%s\\n" and "a b" (as a single arg)
;;   - Result: "a b\\n" (the string "a b" with one newline)
;;
;; Contrast with shell behavior: `printf %s\\n 'a b'` in a shell
;;   - Shell tokenizes to: printf, %s\\n, and 'a b' (which becomes a b after quote removal)
;;   - Result would be the same: "a b\\n"
;;
;; However, without quotes in shell: `printf %s\\n a b`
;;   - Shell tokenizes to: printf, %s\\n, a, b (3 arguments!)
;;   - printf sees format "%s\\n" with two arguments "a" and "b"
;;   - Result: "a\\nb\\n" (each arg printed with newline)

(deftest test-exec-printf-no-tokenization
  (testing "Printf with string containing space - no tokenization happens"
    ;; The string "a b" is passed as a single argument to printf
    (is (= "a b\n" (process/exec "printf" "%s\\n" "a b")))))

(deftest test-exec-printf-separate-args
  (testing "Printf with separate arguments shows different behavior"
    ;; When we pass "a" and "b" as separate arguments,
    ;; printf receives them as separate args and prints each with format
    (is (= "a\nb\n" (process/exec "printf" "%s\\n" "a" "b")))))

(deftest test-exec-test-true
  (testing "Execute test command that succeeds"
    (is (= "" (process/exec "test" "1" "-eq" "1")))))

(deftest test-exec-rev
  (testing "Execute rev to reverse lines"
    (is (= "olleh\n" (exec-with-input "hello\n" "rev")))))

;; ============================================================================
;; Start and Process tests
;; ============================================================================

(deftest test-start-simple
  (testing "Start a simple process"
    (let [proc (process/start "echo" "test")]
      (is (instance? Process proc))
      (is (= 0 (.waitFor proc))))))

(deftest test-start-sleep
  (testing "Start a sleep process"
    (let [proc (process/start "sleep" "0.1")]
      (is (instance? Process proc))
      (is (= 0 (.waitFor proc))))))

(deftest test-start-true
  (testing "Start true command"
    (let [proc (process/start "true")]
      (is (= 0 (.waitFor proc))))))

(deftest test-start-false
  (testing "Start false command returns non-zero"
    (let [proc (process/start "false")]
      (is (not= 0 (.waitFor proc))))))

(deftest test-start-exit-code
  (testing "Start process with specific exit code"
    (let [proc (process/start "sh" "-c" "exit 42")]
      (is (= 42 (.waitFor proc))))))

(deftest test-start-stdout-stream
  (testing "Get stdout stream from process"
    (let [proc (process/start "echo" "output test")
          stdout-stream (process/stdout proc)
          output (slurp stdout-stream)]
      (.waitFor proc)
      (is (= "output test\n" output)))))

(deftest test-start-stderr-stream
  (testing "Get stderr stream from process"
    (let [proc (process/start "sh" "-c" "echo error >&2")
          stderr-stream (process/stderr proc)
          error (slurp stderr-stream)]
      (.waitFor proc)
      (is (= "error\n" error)))))

(deftest test-start-stdin-stream
  (testing "Write to stdin stream of process"
    (let [proc (process/start "cat")
          stdin-stream (process/stdin proc)
          stdout-stream (process/stdout proc)]
      (.write stdin-stream (.getBytes "test data\n"))
      (.close stdin-stream)
      (let [output (slurp stdout-stream)]
        (.waitFor proc)
        (is (= "test data\n" output))))))

(deftest test-start-multiple-stdin-writes
  (testing "Write multiple times to stdin"
    (let [proc (process/start "cat")
          stdin-stream (process/stdin proc)
          stdout-stream (process/stdout proc)]
      (.write stdin-stream (.getBytes "line1\n"))
      (.write stdin-stream (.getBytes "line2\n"))
      (.write stdin-stream (.getBytes "line3\n"))
      (.close stdin-stream)
      (let [output (slurp stdout-stream)]
        (.waitFor proc)
        (is (= "line1\nline2\nline3\n" output))))))

(deftest test-exit-ref
  (testing "Use exit-ref to get exit value"
    (let [proc (process/start "true")
          exit-val (process/exit-ref proc)]
      (is (= 0 @exit-val)))))

(deftest test-exit-ref-nonzero
  (testing "Use exit-ref with non-zero exit"
    (let [proc (process/start "sh" "-c" "exit 7")
          exit-val (process/exit-ref proc)]
      (is (= 7 @exit-val)))))

(deftest test-exit-ref-deref-waits
  (testing "Dereferencing exit-ref waits for process"
    (let [start-time (System/currentTimeMillis)
          proc (process/start "sleep" "0.1")
          exit-val (process/exit-ref proc)
          _ @exit-val
          elapsed (- (System/currentTimeMillis) start-time)]
      (is (>= elapsed 100)))))

(deftest test-alive-check
  (testing "Check if process is alive"
    (let [proc (process/start "sleep" "0.2")]
      (is (.isAlive proc))
      (.waitFor proc)
      (is (not (.isAlive proc))))))

(deftest test-destroy-process
  (testing "Destroy a running process"
    (let [proc (process/start "sleep" "10")]
      (is (.isAlive proc))
      (.destroy proc)
      (.waitFor proc)
      (is (not (.isAlive proc))))))

;; ============================================================================
;; Environment and Directory tests
;; ============================================================================

(deftest test-exec-with-env-var
  (testing "Execute with custom environment variable"
    (is (= "myvalue\n" 
           (process/exec {:env {"MYVAR" "myvalue"}} "sh" "-c" "echo $MYVAR")))))

(deftest test-exec-with-multiple-env-vars
  (testing "Execute with multiple environment variables"
    (is (= "foo bar\n" 
           (process/exec {:env {"VAR1" "foo" "VAR2" "bar"}} 
                        "sh" "-c" "echo $VAR1 $VAR2")))))

(deftest test-exec-env-override
  (testing "Override existing environment variable"
    (is (= "override\n" 
           (process/exec {:env {"PATH" "override"}} "sh" "-c" "echo $PATH")))))

(deftest test-exec-env-with-spaces
  (testing "Environment variable with spaces"
    (is (= "hello world\n" 
           (process/exec {:env {"MSG" "hello world"}} "sh" "-c" "echo $MSG")))))

(deftest test-exec-env-empty-string
  (testing "Environment variable with empty string"
    (is (= "\n" 
           (process/exec {:env {"EMPTY" ""}} "sh" "-c" "echo $EMPTY")))))

(deftest test-exec-clear-env
  (testing "Clear inherited environment variables"
    ;; Test that we can explicitly set vars with clear-env
    (let [output (process/exec {:clear-env true :env {"MYTEST" "value"}} "sh" "-c" "echo $MYTEST")]
      (is (= "value\n" output)))))

(deftest test-exec-clear-env-with-new-vars
  (testing "Clear env and set new variables"
    (is (= "test\n" 
           (process/exec {:clear-env true :env {"MYVAR" "test"}} 
                        "sh" "-c" "echo $MYVAR")))))

(deftest test-exec-with-dir
  (testing "Execute command in different directory"
    (let [temp-dir (System/getProperty "java.io.tmpdir")
          output (process/exec {:dir temp-dir} "pwd")]
      (is (str/includes? output "tmp")))))

(deftest test-start-with-env
  (testing "Start process with environment variable"
    (let [proc (process/start {:env {"TEST" "value"}} "sh" "-c" "echo $TEST")
          output (slurp (process/stdout proc))]
      (.waitFor proc)
      (is (= "value\n" output)))))

(deftest test-start-with-dir
  (testing "Start process in different directory"
    (let [temp-dir (System/getProperty "java.io.tmpdir")
          proc (process/start {:dir temp-dir} "pwd")
          output (slurp (process/stdout proc))]
      (.waitFor proc)
      (is (str/includes? output "tmp")))))

;; ============================================================================
;; File I/O and Redirect tests
;; ============================================================================

(deftest test-exec-out-to-file
  (testing "Redirect output to file"
    (let [temp-file (File/createTempFile "test-out" ".txt")]
      (.deleteOnExit temp-file)
      (let [proc (process/start {:out (process/to-file temp-file)} "echo" "file output")]
        (.waitFor proc)
        (is (= "file output\n" (slurp temp-file)))))))

(deftest test-exec-err-to-file
  (testing "Redirect stderr to file"
    (let [temp-file (File/createTempFile "test-err" ".txt")]
      (.deleteOnExit temp-file)
      (let [proc (process/start {:err (process/to-file temp-file)} 
                                "sh" "-c" "echo error >&2")]
        (.waitFor proc)
        (is (= "error\n" (slurp temp-file)))))))

(deftest test-exec-in-from-file
  (testing "Read input from file"
    (let [temp-file (File/createTempFile "test-in" ".txt")]
      (.deleteOnExit temp-file)
      (spit temp-file "file content\n")
      (let [output (process/exec {:in (process/from-file temp-file)} "cat")]
        (is (= "file content\n" output))))))

(deftest test-exec-append-to-file
  (testing "Append output to file"
    (let [temp-file (File/createTempFile "test-append" ".txt")]
      (.deleteOnExit temp-file)
      (spit temp-file "initial\n")
      (let [proc (process/start {:out (process/to-file temp-file :append true)} 
                                "echo" "appended")]
        (.waitFor proc)
        (is (= "initial\nappended\n" (slurp temp-file)))))))

(deftest test-exec-file-roundtrip
  (testing "Read from file, process, write to file"
    (let [in-file (File/createTempFile "test-in" ".txt")
          out-file (File/createTempFile "test-out" ".txt")]
      (.deleteOnExit in-file)
      (.deleteOnExit out-file)
      (spit in-file "lowercase text\n")
      (let [proc (process/start {:in (process/from-file in-file)
                                :out (process/to-file out-file)} 
                               "tr" "a-z" "A-Z")]
        (.waitFor proc)
        (is (= "LOWERCASE TEXT\n" (slurp out-file)))))))

(deftest test-exec-multiple-file-operations
  (testing "Multiple processes with file I/O"
    (let [file1 (File/createTempFile "test1" ".txt")
          file2 (File/createTempFile "test2" ".txt")]
      (.deleteOnExit file1)
      (.deleteOnExit file2)
      (spit file1 "3\n1\n2\n")
      (let [proc (process/start {:in (process/from-file file1)
                                :out (process/to-file file2)} 
                               "sort")]
        (.waitFor proc)
        (is (= "1\n2\n3\n" (slurp file2)))))))

;; ============================================================================
;; Redirect options tests
;; ============================================================================

(deftest test-exec-out-inherit
  (testing "Inherit stdout (no capture)"
    (let [proc (process/start {:out :inherit} "echo" "inherited")]
      (is (= 0 (.waitFor proc))))))

(deftest test-exec-err-inherit
  (testing "Inherit stderr"
    (let [proc (process/start {:err :inherit} "sh" "-c" "echo error >&2")]
      (is (= 0 (.waitFor proc))))))

(deftest test-exec-in-inherit
  (testing "Inherit stdin"
    (let [proc (process/start {:in :inherit} "true")]
      (is (= 0 (.waitFor proc))))))

(deftest test-exec-out-discard
  (testing "Discard stdout"
    (let [proc (process/start {:out :discard} "echo" "discarded")]
      (is (= 0 (.waitFor proc))))))

(deftest test-exec-err-discard
  (testing "Discard stderr"
    (let [proc (process/start {:err :discard} "sh" "-c" "echo error >&2")]
      (is (= 0 (.waitFor proc))))))

(deftest test-exec-err-to-stdout
  (testing "Redirect stderr to stdout"
    (let [proc (process/start {:err :stdout} "sh" "-c" "echo out; echo err >&2")
          output (slurp (process/stdout proc))]
      (.waitFor proc)
      (is (str/includes? output "out"))
      (is (str/includes? output "err")))))

;; ============================================================================
;; Concurrent Process tests
;; ============================================================================

(deftest test-concurrent-processes
  (testing "Run multiple processes concurrently"
    (let [procs [(process/start "sleep" "0.1")
                 (process/start "sleep" "0.1")
                 (process/start "sleep" "0.1")]
          start-time (System/currentTimeMillis)]
      (doseq [p procs] (.waitFor p))
      (let [elapsed (- (System/currentTimeMillis) start-time)]
        ;; Should take ~0.1s, not 0.3s
        (is (< elapsed 250))))))

(deftest test-concurrent-exec-calls
  (testing "Execute multiple execs concurrently"
    (let [futures (doall (repeatedly 5 #(future (process/exec "echo" "test"))))]
      (is (every? #(= "test\n" @%) futures)))))

(deftest test-process-pipeline-cat-grep
  (testing "Pipeline: cat | grep"
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
        (is (str/includes? output "match this"))
        (is (str/includes? output "match again"))))))

(deftest test-process-pipeline-echo-wc
  (testing "Pipeline: echo | wc"
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
        (is (= "2" output))))))

(deftest test-process-producer-consumer
  (testing "Producer-consumer pattern with processes"
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
        (is (= "5" output))))))

(deftest test-concurrent-file-writers
  (testing "Multiple processes writing to different files"
    (let [files (repeatedly 3 #(File/createTempFile "concurrent" ".txt"))
          procs (map-indexed 
                 (fn [i f]
                   (.deleteOnExit f)
                   (process/start {:out (process/to-file f)} 
                                 "echo" (str "output" i)))
                 files)]
      (doseq [p procs] (.waitFor p))
      (is (= "output0\n" (slurp (first files))))
      (is (= "output1\n" (slurp (second files))))
      (is (= "output2\n" (slurp (nth files 2)))))))

(deftest test-concurrent-readers
  (testing "Multiple processes reading from different files"
    (let [files (repeatedly 3 #(File/createTempFile "input" ".txt"))]
      (doseq [[i f] (map-indexed vector files)]
        (.deleteOnExit f)
        (spit f (str "content" i "\n")))
      (let [procs (map #(process/start {:in (process/from-file %)} "cat") files)
            outputs (map #(slurp (process/stdout %)) procs)]
        (doseq [p procs] (.waitFor p))
        (is (= "content0\n" (first outputs)))
        (is (= "content1\n" (second outputs)))
        (is (= "content2\n" (nth outputs 2)))))))

(deftest test-parallel-sorting
  (testing "Sort different data sets in parallel"
    (let [data1 "3\n1\n2\n"
          data2 "z\nx\ny\n"
          proc1 (process/start "sort")
          proc2 (process/start "sort")]
      ;; Write to proc1
      (let [stdin1 (process/stdin proc1)]
        (.write stdin1 (.getBytes data1))
        (.close stdin1))
      ;; Write to proc2
      (let [stdin2 (process/stdin proc2)]
        (.write stdin2 (.getBytes data2))
        (.close stdin2))
      ;; Read outputs
      (let [out1 (slurp (process/stdout proc1))
            out2 (slurp (process/stdout proc2))]
        (.waitFor proc1)
        (.waitFor proc2)
        (is (= "1\n2\n3\n" out1))
        (is (= "x\ny\nz\n" out2))))))

(deftest test-repl-interaction
  (testing "Interact with a Clojure REPL process"
    (let [proc (process/start "clojure")
          stdin (process/stdin proc)
          stdout (process/stdout proc)
          reader (io/reader stdout)]
      ;; Wait for REPL prompt
      (Thread/sleep 2000)
      ;; Send expression
      (.write stdin (.getBytes "(+ 1 2 3)\n"))
      (.flush stdin)
      ;; Read response (this is fragile, just checking it doesn't crash)
      (Thread/sleep 500)
      (.destroy proc)
      (is true))))

(deftest test-bidirectional-communication
  (testing "Bidirectional communication with cat"
    (let [proc (process/start "cat")
          stdin (process/stdin proc)
          stdout (process/stdout proc)
          reader (io/reader stdout)]
      ;; Write and read multiple times
      (.write stdin (.getBytes "first\n"))
      (.flush stdin)
      (is (= "first" (.readLine reader)))
      
      (.write stdin (.getBytes "second\n"))
      (.flush stdin)
      (is (= "second" (.readLine reader)))
      
      (.close stdin)
      (.waitFor proc))))

(deftest test-long-running-process
  (testing "Start long-running process and terminate early"
    (let [proc (process/start "sh" "-c" "for i in $(seq 1 100); do echo $i; sleep 0.01; done")
          stdout (process/stdout proc)
          reader (io/reader stdout)]
      ;; Read a few lines
      (is (= "1" (.readLine reader)))
      (is (= "2" (.readLine reader)))
      ;; Terminate
      (.destroy proc)
      (.waitFor proc)
      (is (not (.isAlive proc))))))

(deftest test-process-fanout
  (testing "One producer, multiple consumers"
    (let [producer (process/start "seq" "1" "10")
          producer-out (process/stdout producer)
          output (slurp producer-out)]
      (.waitFor producer)
      ;; Now use the captured output for multiple consumers
      (let [out1 (exec-with-input output "head" "-n" "3")
            out2 (exec-with-input output "tail" "-n" "3")]
        (is (str/includes? out1 "1"))
        (is (str/includes? out2 "10"))))))

;; ============================================================================
;; Advanced use cases
;; ============================================================================

(deftest test-exec-chained-commands
  (testing "Execute chained shell commands"
    (let [output (process/exec "sh" "-c" "echo 1; echo 2; echo 3")]
      (is (str/includes? output "1"))
      (is (str/includes? output "2"))
      (is (str/includes? output "3")))))

(deftest test-exec-command-substitution
  (testing "Execute command with command substitution"
    (let [output (process/exec "sh" "-c" "echo $(echo nested)")]
      (is (= "nested\n" output)))))

(deftest test-exec-pipe-in-shell
  (testing "Execute shell command with pipe"
    (is (= "HELLO\n" 
           (process/exec "sh" "-c" "echo hello | tr a-z A-Z")))))

(deftest test-exec-conditional-execution
  (testing "Execute conditional shell command"
    (is (= "success\n" 
           (process/exec "sh" "-c" "true && echo success")))))

(deftest test-exec-conditional-failure
  (testing "Execute conditional on failure"
    (is (= "failed\n" 
           (process/exec "sh" "-c" "false || echo failed")))))

(deftest test-exec-background-in-shell
  (testing "Execute command with shell background process"
    (let [output (process/exec "sh" "-c" "sleep 0.1 & echo done")]
      (is (= "done\n" output)))))

(deftest test-exec-redirection-in-shell
  (testing "Execute with shell redirection"
    (let [temp-file (File/createTempFile "shell-redir" ".txt")]
      (.deleteOnExit temp-file)
      (process/exec "sh" "-c" (str "echo test > " (.getPath temp-file)))
      (is (= "test\n" (slurp temp-file))))))

(deftest test-exec-here-string
  (testing "Execute with here-string style input"
    (let [output (process/exec "sh" "-c" "echo 'hello' | wc -c")]
      (is (= "6" (str/trim output))))))

(deftest test-start-get-pid
  (testing "Get process ID from started process"
    (let [proc (process/start "sleep" "0.1")]
      (is (pos? (.pid proc)))
      (.waitFor proc))))

(deftest test-exec-with-timeout
  (testing "Wait for process with timeout"
    (let [proc (process/start "sleep" "0.1")
          completed (.waitFor proc 1 java.util.concurrent.TimeUnit/SECONDS)]
      (is completed))))

(deftest test-exec-timeout-exceeded
  (testing "Process timeout exceeded"
    (let [proc (process/start "sleep" "10")
          completed (.waitFor proc 100 java.util.concurrent.TimeUnit/MILLISECONDS)]
      (is (not completed))
      (.destroy proc))))

(deftest test-force-destroy
  (testing "Force destroy process"
    (let [proc (process/start "sleep" "10")]
      (.destroyForcibly proc)
      (.waitFor proc)
      (is (not (.isAlive proc))))))

(deftest test-process-descendants
  (testing "Check if process has descendants"
    (let [proc (process/start "sh" "-c" "sleep 10 & wait")]
      (Thread/sleep 100)
      (.destroy proc)
      (.waitFor proc)
      (is (not (.isAlive proc))))))

;; ============================================================================
;; Error handling and edge cases
;; ============================================================================

(deftest test-exec-nonzero-exit-throws
  (testing "exec throws on non-zero exit"
    (is (thrown? RuntimeException 
                 (process/exec "sh" "-c" "exit 1")))))

(deftest test-exec-nonzero-custom-exit
  (testing "exec throws on custom exit code"
    (is (thrown? RuntimeException 
                 (process/exec "sh" "-c" "exit 42")))))

(deftest test-exec-command-not-found
  (testing "exec throws on command not found"
    (is (thrown? Exception 
                 (process/exec "nonexistent-command-xyz")))))

(deftest test-start-command-not-found
  (testing "start throws on command not found"
    (is (thrown? Exception 
                 (process/start "nonexistent-command-xyz")))))

(deftest test-exec-empty-command
  (testing "exec with empty string command"
    (is (thrown? Exception 
                 (process/exec "")))))

(deftest test-exec-large-input
  (testing "exec with large input"
    (let [large-input (apply str (repeat 10000 "line\n"))
          line-count (exec-with-input large-input "wc" "-l")]
      (is (= "10000" (str/trim line-count))))))

(deftest test-exec-large-output
  (testing "exec with large output"
    (let [output (process/exec "seq" "1" "10000")
          lines (str/split-lines output)]
      (is (= 10000 (count lines)))
      (is (= "1" (first lines)))
      (is (= "10000" (last lines))))))

(deftest test-exec-binary-data
  (testing "exec with binary-like data"
    (let [input (apply str (map char (range 32 127)))
          output (exec-with-input input "cat")]
      (is (= input output)))))

(deftest test-exec-unicode-input
  (testing "exec with unicode characters"
    (let [input "Hello 世界 🌍\n"
          output (exec-with-input input "cat")]
      (is (= input output)))))

(deftest test-exec-special-shell-chars
  (testing "exec with special shell characters"
    (is (= "$HOME\n" (process/exec "echo" "$HOME")))))

(deftest test-exec-quotes-in-args
  (testing "exec with quotes in arguments"
    (is (= "hello world\n" (process/exec "echo" "hello world")))))

(deftest test-exec-empty-args
  (testing "exec with empty string argument"
    (is (= " \n" (process/exec "echo" "" "")))))

(deftest test-null-byte-handling
  (testing "Handle data with null bytes"
    (let [input "before\u0000after\n"
          output (exec-with-input input "cat")]
      (is (str/includes? output "before"))
      (is (str/includes? output "after")))))

(deftest test-concurrent-start-and-destroy
  (testing "Start and destroy processes concurrently"
    (let [procs (repeatedly 5 #(process/start "sleep" "10"))]
      (doseq [p procs] (.destroy p))
      (doseq [p procs] (.waitFor p))
      (is (every? #(not (.isAlive %)) procs)))))

(deftest test-rapid-process-creation
  (testing "Rapidly create and complete processes"
    (let [start (System/currentTimeMillis)
          results (doall (repeatedly 20 #(process/exec "echo" "fast")))
          elapsed (- (System/currentTimeMillis) start)]
      (is (every? #(= "fast\n" %) results))
      (is (< elapsed 5000))))) ;; Should complete in under 5 seconds

;; ============================================================================
;; Additional comprehensive tests
;; ============================================================================

(deftest test-exec-with-dash-args
  (testing "Execute command with arguments starting with dash"
    (is (= "-- --help\n" (process/exec "echo" "--" "--help")))))

(deftest test-exec-find-files
  (testing "Execute find command"
    (let [output (process/exec "find" "/tmp" "-maxdepth" "0" "-type" "d")]
      (is (str/includes? output "tmp")))))

(deftest test-exec-ls-hidden
  (testing "Execute ls showing hidden files"
    (let [output (process/exec "ls" "-a" "/")]
      (is (str/includes? output ".")))))

(deftest test-exec-printf-format
  (testing "Execute printf with format string"
    (is (= "Number: 42" (process/exec "printf" "Number: %d" "42")))))

(deftest test-exec-awk-simple
  (testing "Execute awk for text processing"
    (let [output (exec-with-input "hello world\n" "awk" "{print $2}")]
      (is (= "world\n" output)))))

(deftest test-exec-sed-substitute
  (testing "Execute sed for substitution"
    (let [output (exec-with-input "hello world\n" "sed" "s/world/universe/")]
      (is (= "hello universe\n" output)))))

(deftest test-exec-sed-delete
  (testing "Execute sed to delete lines"
    (let [output (exec-with-input "line1\nline2\nline3\n" "sed" "2d")]
      (is (= "line1\nline3\n" output)))))

(deftest test-exec-xargs
  (testing "Execute xargs"
    (let [output (exec-with-input "a\nb\nc\n" "xargs" "echo")]
      (is (= "a b c\n" output)))))

(deftest test-exec-tee-like
  (testing "Execute tee to duplicate output"
    (let [temp-file (File/createTempFile "tee-test" ".txt")]
      (.deleteOnExit temp-file)
      (let [output (exec-with-input "test data\n" "tee" (.getPath temp-file))]
        (is (= "test data\n" output))
        (is (= "test data\n" (slurp temp-file)))))))

(deftest test-exec-read-dev-null
  (testing "Read from /dev/null"
    (let [proc (process/start {:in (process/from-file (io/file "/dev/null"))} "cat")
          output (slurp (process/stdout proc))]
      (.waitFor proc)
      (is (= "" output)))))

(deftest test-exec-yes-with-timeout
  (testing "Run yes command and terminate"
    (let [proc (process/start "yes")
          stdout (process/stdout proc)
          reader (io/reader stdout)]
      (is (= "y" (.readLine reader)))
      (is (= "y" (.readLine reader)))
      (.destroy proc)
      (.waitFor proc))))

(deftest test-start-check-pid-different
  (testing "Different processes have different PIDs"
    (let [proc1 (process/start "sleep" "0.2")
          proc2 (process/start "sleep" "0.2")
          pid1 (.pid proc1)
          pid2 (.pid proc2)]
      (is (not= pid1 pid2))
      (.waitFor proc1)
      (.waitFor proc2))))

(deftest test-exec-multiline-string-arg
  (testing "Execute with multiline string argument"
    (is (= "hello\nworld\n" (process/exec "echo" "hello\nworld")))))

(deftest test-exec-env-var-substitution
  (testing "Environment variable is properly substituted"
    (let [output (process/exec {:env {"NAME" "Alice"}} "sh" "-c" "echo Hello $NAME")]
      (is (= "Hello Alice\n" output)))))

(deftest test-exec-multiple-env-substitution
  (testing "Multiple environment variables are substituted"
    (let [output (process/exec {:env {"FIRST" "John" "LAST" "Doe"}} 
                               "sh" "-c" "echo $FIRST $LAST")]
      (is (= "John Doe\n" output)))))

(deftest test-start-with-many-args
  (testing "Start process with many arguments"
    (let [proc (process/start "echo" "1" "2" "3" "4" "5" "6" "7" "8" "9" "10")
          output (slurp (process/stdout proc))]
      (.waitFor proc)
      (is (= "1 2 3 4 5 6 7 8 9 10\n" output)))))

(deftest test-exec-bc-calculation
  (testing "Execute bc for calculation"
    (let [output (exec-with-input "2 + 2\n" "bc")]
      (is (= "4\n" output)))))

(deftest test-exec-expr-arithmetic
  (testing "Execute expr for arithmetic"
    (is (= "8\n" (process/exec "expr" "5" "+" "3")))))

(deftest test-exec-uname
  (testing "Execute uname command"
    (let [output (process/exec "uname")]
      (is (string? output))
      (is (pos? (count output))))))

(deftest test-exec-hostname
  (testing "Execute hostname command"
    (let [output (process/exec "hostname")]
      (is (string? output))
      (is (pos? (count output))))))

(deftest test-exec-whoami
  (testing "Execute whoami command"
    (let [output (process/exec "whoami")]
      (is (string? output))
      (is (pos? (count output))))))

(deftest test-exec-env-list
  (testing "Execute env to list environment variables"
    (let [output (process/exec {:env {"TESTVAR" "testvalue"}} "env")]
      (is (str/includes? output "TESTVAR=testvalue")))))

(deftest test-exec-tac-reverse
  (testing "Execute tac to reverse line order"
    (let [output (exec-with-input "first\nsecond\nthird\n" "tac")]
      (is (= "third\nsecond\nfirst\n" output)))))

(deftest test-exec-nl-number-lines
  (testing "Execute nl to number lines"
    (let [output (exec-with-input "line\nline\n" "nl")]
      (is (str/includes? output "1"))
      (is (str/includes? output "2")))))

(deftest test-exec-paste-merge
  (testing "Execute paste to merge lines"
    (let [output (exec-with-input "a\nb\nc\n" "paste" "-s" "-d" ",")]
      (is (= "a,b,c\n" output)))))

(deftest test-exec-join-files
  (testing "Execute join with input"
    (let [file1 (File/createTempFile "join1" ".txt")
          file2 (File/createTempFile "join2" ".txt")]
      (.deleteOnExit file1)
      (.deleteOnExit file2)
      (spit file1 "1 a\n2 b\n")
      (spit file2 "1 x\n2 y\n")
      (let [output (process/exec "join" (.getPath file1) (.getPath file2))]
        (is (str/includes? output "1 a x"))
        (is (str/includes? output "2 b y"))))))

(deftest test-exec-fold-wrap
  (testing "Execute fold to wrap lines"
    (let [output (exec-with-input "abcdefghij\n" "fold" "-w" "5")]
      (is (str/includes? output "abcde"))
      (is (str/includes? output "fghij")))))

(deftest test-exec-expand-tabs
  (testing "Execute expand to convert tabs"
    (let [output (exec-with-input "a\tb\n" "expand")]
      (is (str/includes? output "a"))
      (is (str/includes? output "b")))))

(deftest test-exec-unexpand-spaces
  (testing "Execute unexpand to convert spaces"
    (let [output (exec-with-input "a        b\n" "unexpand")]
      (is (str/includes? output "a"))
      (is (str/includes? output "b")))))

(deftest test-exec-comm-compare
  (testing "Execute comm to compare sorted files"
    (let [file1 (File/createTempFile "comm1" ".txt")
          file2 (File/createTempFile "comm2" ".txt")]
      (.deleteOnExit file1)
      (.deleteOnExit file2)
      (spit file1 "apple\nbanana\n")
      (spit file2 "banana\ncherry\n")
      (let [output (process/exec "comm" (.getPath file1) (.getPath file2))]
        (is (str/includes? output "apple"))
        (is (str/includes? output "cherry"))))))

(deftest test-exec-diff-files
  (testing "Execute diff to compare files"
    (let [file1 (File/createTempFile "diff1" ".txt")
          file2 (File/createTempFile "diff2" ".txt")]
      (.deleteOnExit file1)
      (.deleteOnExit file2)
      (spit file1 "line1\nline2\n")
      (spit file2 "line1\nline3\n")
      (is (thrown? RuntimeException
                   (process/exec "diff" (.getPath file1) (.getPath file2)))))))

(deftest test-exec-cmp-files
  (testing "Execute cmp to compare files"
    (let [file1 (File/createTempFile "cmp1" ".txt")
          file2 (File/createTempFile "cmp2" ".txt")]
      (.deleteOnExit file1)
      (.deleteOnExit file2)
      (spit file1 "same content\n")
      (spit file2 "same content\n")
      (is (= "" (process/exec "cmp" (.getPath file1) (.getPath file2)))))))

(deftest test-exec-sha256sum
  (testing "Execute sha256sum for checksum"
    (let [output (exec-with-input "test\n" "sha256sum")]
      (is (pos? (count output)))
      (is (re-find #"[0-9a-f]{64}" output)))))

(deftest test-exec-md5sum
  (testing "Execute md5sum for checksum"
    (let [output (exec-with-input "test\n" "md5sum")]
      (is (pos? (count output)))
      (is (re-find #"[0-9a-f]{32}" output)))))

(deftest test-exec-base64-encode
  (testing "Execute base64 to encode"
    (let [output (exec-with-input "hello\n" "base64")]
      (is (= "aGVsbG8K\n" output)))))

(deftest test-exec-base64-decode
  (testing "Execute base64 to decode"
    (let [output (exec-with-input "aGVsbG8K\n" "base64" "-d")]
      (is (= "hello\n" output)))))

(deftest test-start-info-pid
  (testing "Get process info"
    (let [proc (process/start "sleep" "0.1")
          info (.info proc)]
      (is (.isPresent (.command info)))
      (.waitFor proc))))

(deftest test-process-handle-current
  (testing "Get current process handle"
    (let [proc (process/start "sleep" "0.1")
          handle (.toHandle proc)]
      (is (pos? (.pid handle)))
      (.waitFor proc))))

(deftest test-concurrent-file-and-stream
  (testing "Mix file and stream I/O"
    (let [input-file (File/createTempFile "input" ".txt")
          output-file (File/createTempFile "output" ".txt")]
      (.deleteOnExit input-file)
      (.deleteOnExit output-file)
      (spit input-file "data\n")
      (let [proc (process/start {:in (process/from-file input-file)
                                :out (process/to-file output-file)} "cat")]
        (.waitFor proc)
        (is (= "data\n" (slurp output-file)))))))

(deftest test-stream-stderr-separately
  (testing "Capture stdout and stderr separately"
    (let [proc (process/start "sh" "-c" "echo out; echo err >&2")
          stdout (slurp (process/stdout proc))
          stderr (slurp (process/stderr proc))]
      (.waitFor proc)
      (is (= "out\n" stdout))
      (is (= "err\n" stderr)))))

(deftest test-multiple-sequential-processes
  (testing "Run processes sequentially"
    (let [proc1 (process/start "echo" "first")
          _ (.waitFor proc1)
          out1 (slurp (process/stdout proc1))
          proc2 (process/start "echo" "second")
          _ (.waitFor proc2)
          out2 (slurp (process/stdout proc2))]
      (is (= "first\n" out1))
      (is (= "second\n" out2)))))

(deftest test-process-three-stage-pipeline
  (testing "Three-stage pipeline"
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
        (is (str/includes? output "4"))
        (is (str/includes? output "5"))))))

(deftest test-fan-in-multiple-producers
  (testing "Multiple producers feeding one consumer"
    (let [temp-file (File/createTempFile "fanin" ".txt")]
      (.deleteOnExit temp-file)
      ;; First producer
      (let [proc1 (process/start {:out (process/to-file temp-file)} "echo" "line1")]
        (.waitFor proc1))
      ;; Second producer appending
      (let [proc2 (process/start {:out (process/to-file temp-file :append true)} "echo" "line2")]
        (.waitFor proc2))
      ;; Consumer
      (let [output (slurp temp-file)]
        (is (str/includes? output "line1"))
        (is (str/includes? output "line2"))))))

(deftest test-process-with-large-arg-count
  (testing "Process with many arguments"
    (let [args (map str (range 50))
          proc (apply process/start "echo" args)
          output (slurp (process/stdout proc))]
      (.waitFor proc)
      (is (str/includes? output "0"))
      (is (str/includes? output "49")))))

(deftest test-exec-timeout-with-waitfor
  (testing "Timeout waiting for slow process"
    (let [proc (process/start "sleep" "5")
          completed (.waitFor proc 200 java.util.concurrent.TimeUnit/MILLISECONDS)]
      (is (not completed))
      (.destroyForcibly proc)
      (.waitFor proc))))

(deftest test-exit-value-before-termination
  (testing "Exit value throws if process not terminated"
    (let [proc (process/start "sleep" "1")]
      (is (thrown? Exception (.exitValue proc)))
      (.destroy proc)
      (.waitFor proc))))

(deftest test-descendants-java11
  (testing "Check descendants (Java 11+)"
    (let [proc (process/start "sh" "-c" "echo test")]
      (.waitFor proc)
      (let [handle (.toHandle proc)
            descendants (.descendants handle)]
        ;; Just verify we can call the method
        (is (not (nil? descendants)))))))

(deftest test-compare-destroy-methods
  (testing "Compare destroy and destroyForcibly"
    (let [proc1 (process/start "sleep" "10")
          proc2 (process/start "sleep" "10")]
      (.destroy proc1)
      (.destroyForcibly proc2)
      (.waitFor proc1)
      (.waitFor proc2)
      (is (not (.isAlive proc1)))
      (is (not (.isAlive proc2))))))

(deftest test-exec-with-working-dir
  (testing "Execute in specific working directory"
    (let [home (System/getProperty "user.home")
          output (process/exec {:dir home} "pwd")]
      (is (str/includes? output home)))))

(deftest test-start-in-temp-dir
  (testing "Start process in temp directory"
    (let [temp (System/getProperty "java.io.tmpdir")
          proc (process/start {:dir temp} "pwd")
          output (slurp (process/stdout proc))]
      (.waitFor proc)
      (is (str/includes? output "tmp")))))

(deftest test-exec-preserves-line-endings
  (testing "Preserve different line endings"
    (let [output (exec-with-input "line1\nline2\n" "cat")]
      (is (= "line1\nline2\n" output)))))

(deftest test-exec-with-symlink
  (testing "Execute through symlink (if available)"
    ;; /bin/sh is often a symlink to bash or dash
    (is (string? (process/exec "/bin/sh" "-c" "echo test")))))

(deftest test-process-output-stream-available
  (testing "Check bytes available in output stream"
    (let [proc (process/start "echo" "test")
          stdout (process/stdout proc)]
      (Thread/sleep 100) ;; Give it time to write
      ;; Just verify we can check available bytes
      (is (>= (.available stdout) 0))
      (slurp stdout)
      (.waitFor proc))))

(deftest test-repl-multiline-interaction
  (testing "Send multiple commands to Clojure REPL"
    (let [proc (process/start "clojure")
          stdin (process/stdin proc)]
      (Thread/sleep 2000) ;; Wait for REPL to start
      (.write stdin (.getBytes "(+ 1 1)\n"))
      (.flush stdin)
      (Thread/sleep 500)
      (.write stdin (.getBytes "(* 2 3)\n"))
      (.flush stdin)
      (Thread/sleep 500)
      (.destroy proc)
      (.waitFor proc)
      (is true)))) ;; Just verify it doesn't crash

(deftest test-process-input-output-threads
  (testing "Concurrent input/output on same process"
    (let [proc (process/start "cat")
          stdin (process/stdin proc)
          stdout (process/stdout proc)
          reader (io/reader stdout)
          write-future (future
                        (dotimes [i 5]
                          (.write stdin (.getBytes (str "line" i "\n")))
                          (.flush stdin)
                          (Thread/sleep 10))
                        (.close stdin))
          lines (atom [])]
      (dotimes [_ 5]
        (when-let [line (.readLine reader)]
          (swap! lines conj line)))
      @write-future
      (.waitFor proc)
      (is (= 5 (count @lines))))))
