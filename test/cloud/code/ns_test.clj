(ns cloud.code.ns-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is testing]]
            [cloud.code.ns :as sut]))

(deftest ns-resource-base-test
  (is (= "cloud/testdata/root" (sut/ns->resource-base 'cloud.testdata.root)))
  (is (= "cloud/testdata/cycle_a" (sut/ns->resource-base 'cloud.testdata.cycle-a))))

(deftest find-and-read-test-resource
  (let [res (sut/find-ns-resource 'cloud.testdata.root)]
    (is (some? res))
    (is (.contains (str res) "cloud/testdata/root.clj"))
    (is (.contains (sut/slurp-resource res) "remote-entry"))))

(deftest read-first-form-and-deps-test
  (let [src "(ns demo.core (:require [foo.bar :as fb] [baz.qux]))\n(def x 1)"
        form (sut/read-first-form src)]
    (is (sut/ns-form? form))
    (is (= #{'foo.bar 'baz.qux}
           (sut/extract-ns-deps form)))))

(deftest read-ns-info-test
  (let [{:keys [ns resource source deps]} (sut/read-ns-info 'cloud.testdata.root)]
    (is (= 'cloud.testdata.root ns))
    (is (some? resource))
    (is (.contains source "remote-entry"))
    (is (= #{'cloud.testdata.mid} deps))))
