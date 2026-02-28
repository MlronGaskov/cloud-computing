(ns cloud.demo.app
  (:require [cloud.remote :as remote]))

(defn heavy [n]
  (reduce + (map #(* % %) (range n))))

(remote/defremote remote-heavy [n]
                  (heavy n))

(defn -main [& _]
  (remote/connect! {:host (or (System/getenv "COORDINATOR_HOST") "localhost")
                    :port (or (some-> (System/getenv "COORDINATOR_PORT") parse-long) 8080)})
  (remote/deploy!)
  (println (remote-heavy 200000))
  (println (remote-heavy 220000))
  (println (remote-heavy 240000)))