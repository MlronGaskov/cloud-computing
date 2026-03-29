(ns cloud.demo.demo1.geo
  (:require [cloud.demo.demo1.util.math :as math]))

(defn speed-kmh [{:keys [priority package-kg]}]
  (let [base (case priority
               :express 48
               :standard 42
               40)
        weight-penalty (* 1.2 (double (or package-kg 0)))
        speed (- base weight-penalty)]
    (math/clamp speed 20 55)))

(defn eta-min [{:keys [distance-km] :as delivery}]
  (let [speed (speed-kmh delivery)
        hours (/ (double distance-km) speed)]
    (math/round2 (* hours 60.0))))
