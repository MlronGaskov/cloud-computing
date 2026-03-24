(ns cloud.test-runner
  (:require [clojure.test :as t]
            cloud.code.ns-test
            cloud.code.bundle-test
            cloud.code.loader-test
            cloud.code.isolate-test
            cloud.net.codec-test
            cloud.java.stream-test
            cloud.coordinator.state-test
            cloud.coordinator.http-test
            cloud.remote-test
            cloud.worker.runtime-test
            cloud.worker.http-test)
  (:gen-class))

(defn -main [& _]
  (let [{:keys [fail error] :as summary}
        (apply t/run-tests
               '[cloud.code.ns-test
                 cloud.code.bundle-test
                 cloud.code.loader-test
                 cloud.code.isolate-test
                 cloud.net.codec-test
                 cloud.java.stream-test
                 cloud.coordinator.state-test
                 cloud.coordinator.http-test
                 cloud.remote-test
                 cloud.worker.runtime-test
                 cloud.worker.http-test])]
    (shutdown-agents)
    (when (pos? (+ fail error))
      (System/exit 1))))
