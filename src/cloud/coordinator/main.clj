(ns cloud.coordinator.main
  (:require [cloud.coordinator.http :as ch]
            [cloud.coordinator.state :as state]
            [cloud.net.http :as http]
            [cloud.util.log :as log]))

(defn -main [& _]
  (let [port (or (some-> (System/getenv "COORDINATOR_PORT") parse-long) 8080)
        st (state/new-state)
        server (http/start-server! {:port   port
                                    :routes (ch/routes st)})]
    (log/info "coordinator up" {:port port})
    (.addShutdownHook (Runtime/getRuntime)
                      (Thread. (fn [] (http/stop-server! server))))
    (loop []
      (Thread/sleep 60000)
      (recur))))