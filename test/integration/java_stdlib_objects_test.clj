(ns integration.java-stdlib-objects-test
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [cloud.remote :as remote])
  (:import (java.time Instant)
           (java.util UUID Date ArrayList HashMap)))

(remote/defremote remote-java-stdlib-test [m]
  {:uuid-same (= (:uuid m) (:uuid m))
   :instant-class (.getName (class (:instant m)))
   :date-ms (.getTime ^Date (:date m))
   :list-size (.size ^ArrayList (:list m))
   :map-size (.size ^HashMap (:map m))
   :list-first (.get ^ArrayList (:list m) 0)
   :map-x (.get ^HashMap (:map m) "x")})

(use-fixtures
  :once
  (fn [f]
    (remote/connect! {:host (or (System/getenv "COORDINATOR_HOST") "localhost")
                      :port (or (some-> (System/getenv "COORDINATOR_PORT") parse-long) 8080)})
    (remote/deploy!)
    (f)))

(deftest java-stdlib-objects-work-remotely
  (let [u (UUID/randomUUID)
        i (Instant/now)
        d (Date.)
        al (doto (ArrayList.) (.add "a") (.add "b"))
        hm (doto (HashMap.) (.put "x" 1) (.put "y" 2))
        payload {:uuid u :instant i :date d :list al :map hm}
        res (remote-java-stdlib-test payload)]
    (is (= true (:uuid-same res)))
    (is (= "java.time.Instant" (:instant-class res)))
    (is (= (.getTime d) (:date-ms res)))
    (is (= 2 (:list-size res)))
    (is (= 2 (:map-size res)))
    (is (= "a" (:list-first res)))
    (is (= 1 (:map-x res)))))