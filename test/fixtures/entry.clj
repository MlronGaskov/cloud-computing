(ns fixtures.entry
    (:require [clojure.set :as set]
      [fixtures.liba :as a]))

(defn run-job [m]
      (let [s (a/shout (:name m))
            v (a/calc (:x m))
            merged (set/union #{:a} #{:b})]
           {:name   s
            :value  v
            :merged merged}))