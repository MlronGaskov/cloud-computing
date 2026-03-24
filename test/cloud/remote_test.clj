(ns cloud.remote-test
  (:require [clojure.test :refer [deftest is]]
            [cloud.remote :as sut]))

(sut/defremote sample-remote {:workers 2 :threads 2} [x]
               (* x x))

(sut/defremote sample-dmap {:workers 2 :threads 2} [x]
               (* x 10))

(deftest defremote-registers-functions-and-chooses-scalar-path-test
  (is (contains? @sut/registry "cloud.remote-test/sample-remote"))
  (is (contains? @sut/registry "cloud.remote-test/sample-remote#batch"))
  (with-redefs [sut/call (fn [_ x] {:ok true :result (* x x)})]
    (is (= 49 (sample-remote 7)))))

(deftest defremote-chooses-distributed-map-path-test
  (with-redefs [sut/dmap* (fn [_ items] (mapv #(* % 10) items))]
    (is (= [10 20 30] (sample-dmap [1 2 3])))))