(ns cloud.net.codec
  (:require [clojure.edn :as edn]))

(defn encode [x] (pr-str x))
(defn decode [^String s] (edn/read-string s))