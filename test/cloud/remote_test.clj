(ns cloud.remote-test
  (:require [clojure.test :refer [deftest is]]
            [cloud.remote :as sut]))

(sut/defremote sample-remote [x]
  (* x x))

(sut/defdmap sample-dmap {:workers 2 :threads 2} [x]
  (* x 10))

(deftest defremote-registers-only-scalar-path-test
  (is (contains? @sut/registry "cloud.remote-test/sample-remote"))
  (is (not (contains? @sut/registry "cloud.remote-test/sample-remote#batch")))
  (with-redefs [sut/call (fn [_ x] {:ok true :result (* x x)})]
    (is (= 49 (sample-remote 7)))))

(deftest defdmap-registers-batch-and-uses-distributed-map-path-test
  (is (contains? @sut/registry "cloud.remote-test/sample-dmap#batch"))
  (with-redefs [sut/dmap* (fn [_ items] (mapv #(* % 10) items))]
    (is (= [10 20 30] (sample-dmap [1 2 3])))))

(deftest defdmap-rejects-non-collection-input-test
  (is (thrown-with-msg?
       clojure.lang.ExceptionInfo
       #"Distributed map expects a non-map collection"
       (sample-dmap 10))))