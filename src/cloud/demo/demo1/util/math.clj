(ns cloud.demo.demo1.util.math)

(defn clamp [x lo hi]
  (max lo (min hi x)))

(defn round2 [x]
  (/ (Math/round (* 100.0 (double x))) 100.0))
