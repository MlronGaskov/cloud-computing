(ns cloud.code.loader-test
  (:require [clojure.test :refer [deftest is testing]]
            [cloud.code.bundle :as bundle]
            [cloud.code.loader :as sut]))

(deftest load-bundle-invoke-test
  (let [b (-> (bundle/build-bundle 'cloud.testdata.root/remote-entry)
              bundle/transport-bundle)]
    (is (= 50
           (sut/load-bundle! b {:invoke? true :args [5]})))))

(deftest load-bundle-without-invoke-test
  (let [b (-> (bundle/build-bundle 'cloud.testdata.root/remote-entry)
              bundle/transport-bundle)]
    (is (= :loaded
           (sut/load-bundle! b)))))
