(ns cloud.worker.http-test
  (:require [clojure.test :refer [deftest is testing]]
            [cloud.worker.http :as sut]
            [cloud.worker.runtime :as rt]))

(deftest task-route-success-test
  (let [handler (get (sut/routes) "/task")]
    (with-redefs [rt/execute! (fn [req]
                                (is (= {:bundle {:entry 'demo/f} :args [1]} req))
                                123)]
      (is (= {:ok true :result 123}
             (handler {:req {:bundle {:entry 'demo/f} :args [1]}}))))))

(deftest task-route-error-test
  (let [handler (get (sut/routes) "/task")]
    (with-redefs [rt/execute! (fn [_]
                                (throw (ex-info "boom" {})))]
      (let [resp (handler {:req {:bundle {} :args []}})]
        (is (= false (:ok resp)))
        (is (re-find #"boom" (:error resp)))))))
