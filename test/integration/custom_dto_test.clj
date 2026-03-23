(ns integration.custom-dto-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [cloud.remote :as remote])
  (:import (demo.dto Point)
           (java.lang.reflect Constructor Method)))

(remote/defremote remote-point-shift-test {} [p]
  (let [cls (.getClass p)
        get-x (.getMethod cls "getX" (make-array Class 0))
        get-y (.getMethod cls "getY" (make-array Class 0))
        x (.invoke get-x p (object-array 0))
        y (.invoke get-y p (object-array 0))
        ctor (.getConstructor cls (into-array Class [Integer/TYPE Integer/TYPE]))]
    (.newInstance ^Constructor ctor (object-array [(int (inc x)) (int (inc y))]))))

(use-fixtures
  :once
  (fn [f]
    (remote/connect! {:host (or (System/getenv "COORDINATOR_HOST") "localhost")
                      :port (or (some-> (System/getenv "COORDINATOR_PORT") parse-long) 8080)})
    (remote/deploy!)
    (f)))

(defn- call-int-method [obj method-name]
  (let [^Method m (.getMethod (.getClass obj) method-name (make-array Class 0))]
    (.invoke m obj (object-array 0))))

(deftest custom-dto-works-remotely
  (let [p (Point. 10 20)
        res (remote-point-shift-test p)]
    (is (= 11 (call-int-method res "getX")))
    (is (= 21 (call-int-method res "getY")))))