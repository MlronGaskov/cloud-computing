(ns cloud.worker.main
  (:require [cloud.net.http :as http]
            [cloud.util.log :as log]
            [cloud.worker.http :as wh]))

(defn- env [k default]
  (or (System/getenv k) default))

(defn- post [url body timeout-ms]
  (http/post-edn {:url url :body body :timeout-ms timeout-ms}))

(defn- retry!
  [{:keys [what f initial-sleep-ms max-sleep-ms]}]
  (loop [sleep-ms (long (or initial-sleep-ms 200))]
    (let [ok?
          (try
            (f)
            true
            (catch Throwable t
              (log/warn (str what " failed, retrying")
                        {:sleep-ms sleep-ms :ex (str t)})
              false))]
      (if ok?
        true
        (do
          (Thread/sleep sleep-ms)
          (recur (min (long (or max-sleep-ms 5000))
                      (long (* 2 sleep-ms)))))))))

(defn -main [& _]
  (let [id (env "WORKER_ID" (str "worker-" (java.util.UUID/randomUUID)))
        host (env "WORKER_HOST" "worker")
        port (parse-long (env "WORKER_PORT" "8090"))
        coord-host (env "COORDINATOR_HOST" "coordinator")
        coord-port (parse-long (env "COORDINATOR_PORT" "8080"))
        server (http/start-server! {:port port :routes (wh/routes)})
        reg-url (str "http://" coord-host ":" coord-port "/register-worker")
        hb-url (str "http://" coord-host ":" coord-port "/heartbeat")]
    (log/info "worker up" {:worker-id id :host host :port port :coordinator (str coord-host ":" coord-port)})

    (retry! {:what             "register-worker"
             :f                #(post reg-url {:worker-id id :host host :port port} 10000)
             :initial-sleep-ms 200
             :max-sleep-ms     5000})

    (.addShutdownHook (Runtime/getRuntime)
                      (Thread. (fn [] (http/stop-server! server))))

    (future
      (loop []
        (Thread/sleep 5000)
        (try
          (post hb-url {:worker-id id} 5000)
          (catch Throwable t
            (log/warn "heartbeat failed" {:ex (str t)})))
        (recur)))

    (loop []
      (Thread/sleep 60000)
      (recur))))