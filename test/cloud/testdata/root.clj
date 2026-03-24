(ns cloud.testdata.root
  (:require [cloud.testdata.mid :as mid]))

(defn remote-entry [x]
  (* 2 (mid/twice-bump x)))
