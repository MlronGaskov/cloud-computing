(ns cloud.demo.demo1.report
  (:require [cloud.demo.demo1.util.math :as math]))

(defn build [{:keys [destination eta-min price risk priority]}]
  {:summary (str "Delivery to " destination
                 ": " (name priority)
                 ", ETA " (math/round2 eta-min) " min"
                 ", price " (math/round2 price) " EUR"
                 ", risk " (name risk))})
