(ns cloud.demo.demo2.main
  (:require [cloud.java.stream :as jstream]
            [cloud.remote :as remote])
  (:import (demo.dto Point)
           (demo.fn Lambdas
                    Times2Function
                    DivisibleBy3Predicate
                    SumBinaryOperator)
           (java.util ArrayList Date HashMap UUID)))

(defn- build-plan [kind samples]
  (let [f  (case kind
             :lambda (Lambdas/times2)
             :object (Times2Function.))
        p  (case kind
             :lambda (Lambdas/divisibleBy3)
             :object (DivisibleBy3Predicate.))
        op (case kind
             :lambda (Lambdas/sum)
             :object (SumBinaryOperator.))]
    (-> (jstream/stream-of (ArrayList. samples))
        (jstream/map-op f)
        (jstream/filter-op p)
        (jstream/reduce-op (long 0) op))))

(defn- labels [zone priority]
  (doto (HashMap.)
    (.put "zone" zone)
    (.put "priority" priority)))

(defn- route-task [kind x y samples zone priority]
  {:kind kind
   :route-id (UUID/randomUUID)
   :created-at (Date.)
   :origin (Point. x y)
   :labels (labels zone priority)
   :plan (build-plan kind samples)})

(remote/defdmap remote-score-routes {:workers 2 :threads 8} [task]
  (let [^Point origin (:origin task)
        ^Date created-at (:created-at task)
        ^HashMap labels (:labels task)
        score (long (jstream/execute (:plan task)))]
    {:route-id (str (:route-id task))
     :strategy (:kind task)
     :created-at-ms (.getTime created-at)
     :origin [(.getX origin) (.getY origin)]
     :zone (.get labels "zone")
     :priority (.get labels "priority")
     :score score
     :approved? (<= score 60)}))

(def sample-routes
  [(route-task :lambda 10 20 [1 2 3 4 5 6 7 8 9] "north" "high")
   (route-task :object 15 25 [3 4 5 6 7 8 9 10] "west" "normal")
   (route-task :lambda 30 12 [2 4 6 8 10 12] "south" "high")
   (route-task :object 8 40 [5 6 7 8 9 10 11 12] "east" "low")])

(defn -main [& _]
  (remote/connect! {:host (or (System/getenv "COORDINATOR_HOST") "localhost")
                    :port (or (some-> (System/getenv "COORDINATOR_PORT") parse-long) 8080)})
  (remote/deploy!)


  (println "Input tasks:")
  (doseq [t sample-routes]
    (println {:strategy (:kind t)
              :route-id (str (:route-id t))
              :origin [(.getX ^Point (:origin t)) (.getY ^Point (:origin t))]
              :zone (.get ^HashMap (:labels t) "zone")
              :priority (.get ^HashMap (:labels t) "priority")}))

  (println)
  (println "Remote distributed result:")
  (doseq [res (remote-score-routes sample-routes)]
    (prn res)))