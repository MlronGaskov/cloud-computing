(ns cloud.coordinator.state-test
  (:require [clojure.test :refer [deftest is testing]]
            [cloud.coordinator.state :as sut]))

(deftest register-worker-and-heartbeat-test
  (let [st (sut/new-state)]
    (is (= {:ok true}
           (sut/register-worker! st {:worker-id "w1" :host "worker" :port 8090})))
    (let [before (get-in @st [:workers "w1" :last-seen])]
      (Thread/sleep 2)
      (is (= {:ok true} (sut/heartbeat! st "w1")))
      (is (<= before (get-in @st [:workers "w1" :last-seen]))))
    (is (= false (get-in @st [:workers "w1" :busy?])))))

(deftest register-fn-and-lookup-test
  (let [st (sut/new-state)
        bundle {:entry 'demo/x :namespaces {}}]
    (is (= {:ok true}
           (sut/register-fn! st {:fn-id "demo/x" :bundle bundle})))
    (is (= bundle (sut/get-fn-bundle st "demo/x")))))

(deftest queue-operations-test
  (let [st (sut/new-state)
        j1 {:id 1}
        j2 {:id 2}]
    (sut/enqueue! st j1)
    (sut/enqueue! st j2)
    (is (= j1 (sut/dequeue! st)))
    (is (= j2 (sut/dequeue! st)))
    (is (nil? (sut/dequeue! st)))))

(deftest choose-worker-prefers-free-most-recent-test
  (let [st (sut/new-state)]
    (swap! st assoc :workers
           {"w1" {:worker-id "w1" :host "a" :port 1 :busy? false :last-seen 10}
            "w2" {:worker-id "w2" :host "b" :port 2 :busy? true :last-seen 99}
            "w3" {:worker-id "w3" :host "c" :port 3 :busy? false :last-seen 30}})
    (is (= "w3" (:worker-id (sut/choose-worker! st))))))

(deftest mark-busy-and-set-seen-test
  (let [st (sut/new-state)]
    (sut/register-worker! st {:worker-id "w1" :host "worker" :port 8090})
    (sut/mark-busy! st "w1" true)
    (is (= true (get-in @st [:workers "w1" :busy?])))
    (let [before (get-in @st [:workers "w1" :last-seen])]
      (Thread/sleep 2)
      (sut/set-worker-seen! st "w1")
      (is (<= before (get-in @st [:workers "w1" :last-seen]))))))
