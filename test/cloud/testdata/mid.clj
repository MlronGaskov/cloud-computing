(ns cloud.testdata.mid
  (:require [cloud.testdata.leaf :as leaf]))

(defn twice-bump [x]
  (-> x leaf/bump leaf/bump))
