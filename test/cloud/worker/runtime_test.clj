(ns cloud.worker.runtime-test
  (:require [clojure.test :refer [deftest is testing]]
            [cloud.worker.runtime :as sut]
            [cloud.code.loader :as loader]))

(deftest execute-delegates-to-loader-test
  (with-redefs [loader/load-bundle! (fn [bundle opts]
                                      (is (= {:entry 'demo/f} bundle))
                                      (is (= {:invoke? true :args [1 2 3]} opts))
                                      :ok)]
    (is (= :ok
           (sut/execute! {:bundle {:entry 'demo/f}
                          :args [1 2 3]})))))
