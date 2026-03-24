(ns cloud.net.codec-test
  (:require [clojure.test :refer [deftest is testing]]
            [cloud.java.stream :as jstream]
            [cloud.net.codec :as sut])
  (:import (java.io Serializable)
           (java.util.function BinaryOperator Function Predicate)))

(deftest encode-decode-nested-data-test
  (let [payload {:numbers [1 2 3]
                 :nested {:flag true :kw :ok :nil nil}}
        encoded (sut/encode payload)]
    (is (= payload
           (sut/decode encoded)))))

(deftest encode-decode-java-stream-plan-test
  (let [mapper (reify Function Serializable
                 (apply [_ x] (* (long x) 3)))
        pred (reify Predicate Serializable
               (test [_ x] (even? (long x))))
        op (reify BinaryOperator Serializable
             (apply [_ a b] (+ (long a) (long b))))
        plan (-> (jstream/stream-of [1 2 3 4])
                 (jstream/map-op mapper)
                 (jstream/filter-op pred)
                 (jstream/reduce-op 0 op))
        roundtrip (sut/decode (sut/encode plan))]
    (is (= 18 (jstream/execute roundtrip)))))

(deftest raw-clojure-fn-is-rejected-test
  (is (thrown-with-msg?
       clojure.lang.ExceptionInfo
       #"Passing raw Clojure functions/closures as payload is not supported"
       (sut/encode {:bad (fn [x] x)}))))
