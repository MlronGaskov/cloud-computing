(ns cloud.demo.demo1.risk
  (:require [cloud.demo.demo1.weather :as weather]))

(defn level [{:keys [battery-percent weather]}]
  (let [severity (weather/severity weather)
        battery (double (or battery-percent 100))
        score (+ severity
                 (cond
                   (< battery 25) 4
                   (< battery 40) 2
                   :else 0))]
    (cond
      (< score 4) :low
      (< score 7) :medium
      :else :high)))
