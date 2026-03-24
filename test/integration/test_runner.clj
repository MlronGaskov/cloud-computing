(ns integration.test-runner
  (:require [clojure.test :as t]
            [integration.custom-dto-test]
            [integration.java-functional-object-test]
            [integration.java-lambda-test]
            [integration.java-stdlib-objects-test]
            [integration.named-clojure-function-test]
            [integration.stream-plan-test]))

(defn -main [& _]
  (let [result (t/run-tests
                 'integration.custom-dto-test
                 'integration.java-functional-object-test
                 'integration.java-lambda-test
                 'integration.java-stdlib-objects-test
                 'integration.named-clojure-function-test
                 'integration.stream-plan-test)]
    (System/exit
      (if (zero? (+ (:fail result) (:error result))) 0 1))))