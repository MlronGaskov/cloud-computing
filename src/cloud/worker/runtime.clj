(ns cloud.worker.runtime
  (:require [cloud.code.loader :as loader]))

(defn execute!
  [{:keys [bundle args]}]
  (loader/load-bundle! bundle {:invoke? true :args args}))