(ns fixtures.liba
    (:require [clojure.string :as str]
      [fixtures.libb :as b]))

(defn shout [s]
      (-> s str/trim str/upper-case))

(defn calc [x]
      (b/inc2 (* x 10)))