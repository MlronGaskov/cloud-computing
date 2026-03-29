(ns cloud.demo.demo1.weather
  (:require [cloud.demo.demo1.util.math :as math]))

(defn severity [{:keys [wind-kmh precipitation-mm]}]
  (let [wind-part (/ (double (or wind-kmh 0)) 6.0)
        rain-part (* 1.5 (double (or precipitation-mm 0)))
        score (+ wind-part rain-part)]
    (math/round2 (math/clamp score 0 10))))

(defn label [weather]
  (let [s (severity weather)]
    (cond
      (< s 3) :good
      (< s 6) :ok
      :else :bad)))
