(ns clojure.java.process-test
  (:require [clojure.test :refer :all]
            [clojure.java.process :as process]))

(deftest test-run-simple-command
  (testing "Running a simple shell command"
    (let [result (process/run "echo" "Hello world")]
      (is (= 0 (:exit result)))
      (is (= "Hello world\n" (:out result))))))

(deftest test-capture-error
  (testing "Capturing stderr"
    (let [result (process/run "sh" "-c" "echo error 1>&2")]
      (is (= 0 (:exit result)))
      (is (= "error\n" (:err result))))))

(deftest test-environment-vars
  (testing "Passing environment variables"
    (let [result (process/run {:env {"FOO" "bar"}} "sh" "-c" "echo $FOO")]
      (is (= "bar\n" (:out result)))))

(deftest test-nonzero-exit
  (testing "Handling non-zero exit codes"
    (let [result (process/run "sh" "-c" "exit 42")]
      (is (= 42 (:exit result))))))

(deftest test-input-stream
  (testing "Streaming input"
    (let [result (process/run {:in "Clojure rocks!"} "cat")]
      (is (= "Clojure rocks!" (:out result))))))

(deftest test-async-process
  (testing "Running process asynchronously"
    (let [proc (process/process "sleep" "1")]
      (is (not (nil? proc)))
      (.waitFor proc)
      (is (= 0 (.exitValue proc))))))

(comment
  ;; More advanced tests can be added as you discover more features!
  )
