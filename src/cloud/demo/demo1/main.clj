(ns cloud.demo.demo1.main
  (:require [cloud.demo.demo1.pipeline :as pipeline]
            [cloud.remote :as remote]))

(remote/defremote remote-delivery-quote {} [delivery]
  (pipeline/evaluate-delivery delivery))

(def sample-delivery
  {:destination "Old Town"
   :distance-km 14
   :package-kg 2.5
   :priority :express
   :battery-percent 58
   :weather {:wind-kmh 22
             :precipitation-mm 1.3}})

(defn -main [& _]
  (remote/connect! {:host (or (System/getenv "COORDINATOR_HOST") "localhost")
                    :port (or (some-> (System/getenv "COORDINATOR_PORT") parse-long) 8080)})
  (remote/deploy!)
  (println "Input:")
  (prn sample-delivery)
  (println "\nRemote result:")
  (prn (remote-delivery-quote sample-delivery)))
