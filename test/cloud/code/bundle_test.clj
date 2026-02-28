(ns cloud.code.bundle-test
    (:require [clojure.test :refer [deftest is testing]]
      [cloud.code.bundle :as bundle]
      [cloud.code.isolate :as iso]))

(deftest build-bundle-includes-transitive-deps
         (testing "bundle includes fixtures + clojure libs mentioned in ns requires"
                  (let [b (bundle/build-bundle 'fixtures.entry/run-job)
                        nss (set (keys (:namespaces b)))]
                       (is (= 'fixtures.entry (:root-ns b)))
                       (is (= 'fixtures.entry/run-job (:entry b)))

                       (is (contains? nss 'fixtures.entry))
                       (is (contains? nss 'fixtures.liba))
                       (is (contains? nss 'fixtures.libb))

                       (is (contains? nss 'clojure.set))
                       (is (contains? nss 'clojure.string))

                       (is (every? (fn [[_ {:keys [source]}]] (string? source))
                                   (:namespaces b))))))

(deftest load-bundle-and-invoke-entry-in-fresh-jvm
         (testing "bundle can be loaded and entry invoked in isolated fresh JVM"
                  (let [b (bundle/build-bundle 'fixtures.entry/run-job)
                        res (iso/invoke-in-fresh-jvm!
                              b
                              {:args [{:name "  alice "
                                       :x    3}]})]
                       (is (= {:name   "ALICE"
                               :value  32
                               :merged #{:a :b}}
                              res)))))

(deftest load-order-is-topological
         (testing "load-order puts deps before dependents"
                  (let [b (bundle/build-bundle 'fixtures.entry/run-job)
                        order (:load-order b)
                        pos (zipmap order (range))]
                       (is (< (pos 'fixtures.libb) (pos 'fixtures.liba)))
                       (is (< (pos 'fixtures.liba) (pos 'fixtures.entry))))))