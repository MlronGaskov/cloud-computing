(ns cloud.util.log
  (:import (java.time ZoneId ZonedDateTime)
           (java.time.format DateTimeFormatter)))

(defn- ts []
  (let [z (ZoneId/systemDefault)
        f (DateTimeFormatter/ofPattern "uuuu-MM-dd HH:mm:ss.SSS")]
    (.format f (ZonedDateTime/now z))))

(defn log
  ([level msg] (log level msg nil))
  ([level msg data]
   (println (str (ts) " [" (name level) "] " msg
                 (when data (str " " (pr-str data)))))
   (flush)))

(defn info [msg & [data]] (log :info msg data))
(defn warn [msg & [data]] (log :warn msg data))
(defn error [msg & [data]] (log :error msg data))