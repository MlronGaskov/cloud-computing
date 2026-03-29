(ns cloud.demo.demo1.pipeline
  (:require [cloud.demo.demo1.geo :as geo]
            [cloud.demo.demo1.pricing :as pricing]
            [cloud.demo.demo1.report :as report]
            [cloud.demo.demo1.risk :as risk]
            [cloud.demo.demo1.weather :as weather]))

(defn evaluate-delivery [{:keys [weather] :as delivery}]
  (let [weather' (assoc weather :severity (weather/severity weather)
                                :label (weather/label weather))
        enriched (assoc delivery :weather weather')
        eta-min (geo/eta-min enriched)
        price (pricing/delivery-price enriched)
        risk (risk/level enriched)]
    (merge
     {:approved? (not= risk :high)
      :eta-min eta-min
      :price-eur price
      :risk risk
      :weather weather'}
     (report/build {:destination (:destination delivery)
                    :eta-min eta-min
                    :price price
                    :risk risk
                    :priority (:priority delivery)}))))
