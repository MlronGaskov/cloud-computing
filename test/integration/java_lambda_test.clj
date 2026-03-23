(ns integration.java-lambda-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [cloud.remote :as remote])
  (:import (demo.fn Lambdas)
           (java.util.function Function Predicate BinaryOperator)))

(remote/defremote remote-java-function-test {} [f x]
  (.apply ^Function f x))

(remote/defremote remote-java-predicate-test {} [p x]
  (.test ^Predicate p x))

(remote/defremote remote-java-binop-test {} [op a b]
  (.apply ^BinaryOperator op a b))

(use-fixtures
  :once
  (fn [f]
    (remote/connect! {:host (or (System/getenv "COORDINATOR_HOST") "localhost")
                      :port (or (some-> (System/getenv "COORDINATOR_PORT") parse-long) 8080)})
    (remote/deploy!)
    (f)))

(deftest serializable-java-function-works-remotely
  (let [f (Lambdas/times2)]
    (is (= 10 (remote-java-function-test f 5)))))

(deftest serializable-java-predicate-works-remotely
  (let [p (Lambdas/divisibleBy3)]
    (is (= true (remote-java-predicate-test p 6)))
    (is (= false (remote-java-predicate-test p 8)))))

(deftest serializable-java-binop-works-remotely
  (let [op (Lambdas/sum)]
    (is (= 9 (remote-java-binop-test op 4 5)))))