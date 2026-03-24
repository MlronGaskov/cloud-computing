(ns cloud.code.isolate-test
  (:require [clojure.test :refer [deftest is testing]]
            [cloud.code.bundle :as bundle]
            [cloud.code.isolate :as sut]))

(deftest invoke-in-fresh-jvm-success-test
  (let [raw-bundle (bundle/build-bundle 'cloud.testdata.root/remote-entry)]
    (with-redefs [cloud.code.isolate/run-clojure-process!
                  (fn [_]
                    {:exit 0 :out "50\n" :err ""})]
      (is (= 50
             (sut/invoke-in-fresh-jvm! raw-bundle {:args [5]}))))))

(deftest invoke-in-fresh-jvm-failure-test
  (let [raw-bundle (bundle/build-bundle 'cloud.testdata.root/remote-entry)]
    (with-redefs [cloud.code.isolate/run-clojure-process!
                  (fn [_]
                    {:exit 1 :out "" :err "boom"})]
      (is (thrown-with-msg?
            clojure.lang.ExceptionInfo
            #"Fresh JVM execution failed"
            (sut/invoke-in-fresh-jvm! raw-bundle {:args [5]}))))))