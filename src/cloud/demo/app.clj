(ns cloud.demo.app
  (:require [cloud.remote :as remote]))

(defn heavy [n]
  (reduce + (map #(* % %) (range n))))

(remote/defremote remote-heavy {:workers 2 :threads 8} [n]
  (heavy n))

(defn bench []
  (let [xs (vec (range 400000 460000 2000))]
    (println "tasks:" (count xs) "first:" (first xs) "last:" (peek xs))

    (println "\n== remote sequential ==")
    (time
     (doall (map remote-heavy xs)))

    (println "\n== remote distributed ==")
    (time
     (doall (remote-heavy xs)))

    (println "\n(done)")))

(defn -main [& _]
  (remote/connect! {:host (or (System/getenv "COORDINATOR_HOST") "localhost")
                    :port (or (some-> (System/getenv "COORDINATOR_PORT") parse-long) 8080)})
  (remote/deploy!)
  (bench))