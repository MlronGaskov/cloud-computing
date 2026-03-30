(ns integration.stream-plan-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [cloud.java.stream :as jstream]
            [cloud.remote :as remote])
  (:import (demo.fn Lambdas)))

(remote/defremote remote-stream-reduce-test [plan]
  (jstream/execute plan))

(remote/defremote remote-stream-collect-test [plan]
  (jstream/execute plan))

(use-fixtures
  :once
  (fn [f]
    (remote/connect! {:host (or (System/getenv "COORDINATOR_HOST") "localhost")
                      :port (or (some-> (System/getenv "COORDINATOR_PORT") parse-long) 8080)})
    (remote/deploy!)
    (f)))

(deftest stream-plan-reduce-works-remotely
  (let [f (Lambdas/times2)
        p (Lambdas/divisibleBy3)
        r (Lambdas/sum)
        plan (-> (jstream/stream-of [1 2 3 4 5 6 7 8 9])
                 (jstream/map-op f)
                 (jstream/filter-op p)
                 (jstream/reduce-op 0 r))]
    (is (= 36 (remote-stream-reduce-test plan)))))

(deftest stream-plan-collect-works-remotely
  (let [f (Lambdas/times2)
        p (Lambdas/divisibleBy3)
        plan (-> (jstream/stream-of [1 2 3 4 5 6 7 8 9])
                 (jstream/map-op f)
                 (jstream/filter-op p)
                 (jstream/collect-to-list))]
    (is (= [6 12 18]
           (vec (remote-stream-collect-test plan))))))