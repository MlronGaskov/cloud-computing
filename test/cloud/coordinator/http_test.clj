(ns cloud.coordinator.http-test
  (:require [clojure.test :refer [deftest is testing]]
            [cloud.coordinator.http :as sut]
            [cloud.coordinator.state :as state]
            [cloud.net.http :as http]))

(deftest routes-register-worker-test
  (let [st (state/new-state)
        handler (get (sut/routes st) "/register-worker")]
    (is (= {:ok true}
           (handler {:req {:worker-id "w1" :host "worker" :port 8090}})))
    (is (= "worker" (get-in @st [:workers "w1" :host])))))

(deftest routes-register-fn-and-status-test
  (let [st (state/new-state)
        register-fn (get (sut/routes st) "/register-fn")
        status (get (sut/routes st) "/status")]
    (register-fn {:req {:fn-id "demo/f" :bundle {:entry 'demo/f :namespaces {}}}})
    (let [resp (status {})]
      (is (:ok resp))
      (is (= ["demo/f"] (:fns resp)))
      (is (= 0 (:queue-size resp))))))

(deftest invoke-unknown-fn-test
  (let [st (state/new-state)
        handler (get (sut/routes st) "/invoke")]
    (is (= {:ok false :error "Unknown fn-id: missing/fn"}
           (handler {:req {:fn-id "missing/fn" :args [1]}})))))

(deftest dispatch-job-sends-to-worker-and-delivers-response-test
  (let [st (state/new-state)
        p (promise)
        job {:fn-id "demo/f" :args [1 2] :promise p}]
    (with-redefs [state/choose-worker! (fn [_] {:worker-id "w1" :host "worker" :port 8090})
                  state/mark-busy! (fn [_ _ _] nil)
                  state/set-worker-seen! (fn [_ _] nil)
                  state/get-fn-bundle (fn [_ _] {:entry 'demo/f :namespaces {}})
                  http/post-edn (fn [{:keys [url body]}]
                                  (is (= "http://worker:8090/task" url))
                                  (is (= {:bundle {:entry 'demo/f :namespaces {}}
                                          :args [1 2]}
                                         body))
                                  {:ok true :result 42})]
      (is (= :sent (#'cloud.coordinator.http/dispatch-job! st job)))
      (is (= {:ok true :result 42}
             (deref p 2000 ::timeout))))))

(deftest dispatch-job-queues-when-no-worker-test
  (let [st (state/new-state)
        p (promise)
        job {:fn-id "demo/f" :args [1] :promise p}]
    (with-redefs [state/choose-worker! (fn [_] nil)]
      (is (= :queued (#'cloud.coordinator.http/dispatch-job! st job)))
      (is (= 1 (count (:queue @st)))))))
