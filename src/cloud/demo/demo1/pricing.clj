(ns cloud.demo.demo1.pricing
  (:require [cloud.demo.demo1.geo :as geo]
            [cloud.demo.demo1.util.math :as math]))

(defn delivery-price [{:keys [distance-km package-kg priority weather] :as delivery}]
  (let [base 5.0
        distance-cost (* 0.9 (double distance-km))
        weight-cost (* 1.1 (double package-kg))
        priority-cost (case priority
                        :express 6.0
                        :standard 2.5
                        0.0)
        weather-cost (* 0.8 (double (:severity weather)))
        eta-adjustment (* 0.03 (geo/eta-min delivery))]
    (math/round2 (+ base distance-cost weight-cost priority-cost weather-cost eta-adjustment))))
