(ns integration.named-clojure-function-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [cloud.remote :as remote]))

(defn heavy [n]
  (reduce + (map #(* % %) (range n))))

(remote/defremote remote-heavy-test [n]
  (heavy n))

(remote/defdmap remote-heavy-dmap {:workers 2 :threads 8} [n]
  (heavy n))

(use-fixtures
  :once
  (fn [f]
    (remote/connect! {:host (or (System/getenv "COORDINATOR_HOST") "localhost")
                      :port (or (some-> (System/getenv "COORDINATOR_PORT") parse-long) 8080)})
    (remote/deploy!)
    (f)))

(deftest named-clojure-function-works-remotely
  (is (= (heavy 1000)
         (remote-heavy-test 1000))))

(deftest named-clojure-function-works-remotely-via-dmap
  (let [xs [1000 1200 1400 1600]]
    (is (= (mapv heavy xs)
           (remote-heavy-dmap xs)))))