(ns cloud.java.stream-test
  (:require [clojure.test :refer [deftest is testing]]
            [cloud.java.stream :as sut])
  (:import (java.io Serializable)
           (java.util ArrayList)
           (java.util.function BinaryOperator Function Predicate)))

(def mapper
  (reify Function Serializable
    (apply [_ x] (* (long x) 2))))

(def pred
  (reify Predicate Serializable
    (test [_ x] (> (long x) 4))))

(def reducer
  (reify BinaryOperator Serializable
    (apply [_ a b] (+ (long a) (long b)))))

(deftest execute-over-seq-source-test
  (let [plan (-> (sut/stream-of [1 2 3 4])
                 (sut/map-op mapper)
                 (sut/filter-op pred)
                 (sut/collect-to-list))]
    (is (= [6 8] (vec (sut/execute plan))))))

(deftest execute-over-java-collection-test
  (let [plan (-> (sut/stream-of (ArrayList. [1 2 3 4]))
                 (sut/map-op mapper)
                 (sut/reduce-op 0 reducer))]
    (is (= 20 (sut/execute plan)))))

(deftest unsupported-source-throws-test
  (is (thrown-with-msg?
       clojure.lang.ExceptionInfo
       #"Unsupported stream source"
       (sut/execute {:source 42 :ops [] :terminal {:type :collect-to-list}}))))
