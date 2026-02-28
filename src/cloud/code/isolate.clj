(ns cloud.code.isolate
  (:require [clojure.edn :as edn]
            [cloud.code.bundle :as bundle])
  (:import (java.io File)))

(defn- temp-file!
       [prefix suffix]
       (let [f (File/createTempFile prefix suffix)]
            (.deleteOnExit f)
            f))

(defn- run-clojure-process!
       [{:keys [env expr]}]
       (let [pb (ProcessBuilder. ["clojure" "-Srepro" "-M" "-e" expr])]
            (.redirectErrorStream pb false)
            (let [env-map (.environment pb)]
                 (doseq [[k v] env]
                        (.put env-map (str k) (str v))))
            (let [p (.start pb)
                  out-f (future (slurp (.getInputStream p)))
                  err-f (future (slurp (.getErrorStream p)))
                  exit (.waitFor p)
                  out @out-f
                  err @err-f]
                 {:exit exit :out out :err err})))

(defn invoke-in-fresh-jvm!
      [raw-bundle {:keys [args] :or {args []}}]
      (let [req {:bundle (bundle/transport-bundle raw-bundle)
                 :entry  (:entry raw-bundle)
                 :args   args}
            req-file (temp-file! "bundle-req-" ".edn")]
           (spit req-file (pr-str req))

           (let [expr
                 (str
                   "(require 'clojure.edn)\n"
                   "(require 'cloud.code.loader)\n"
                   "(let [p (System/getenv \"BUNDLE_REQ\")\n"
                   "      req (clojure.edn/read-string (slurp p))\n"
                   "      b (:bundle req)\n"
                   "      args (:args req)\n"
                   "      res (cloud.code.loader/load-bundle! b {:invoke? true :args args})]\n"
                   "  (println (pr-str res)))")
                 {:keys [exit out err]}
                 (run-clojure-process! {:env  {"BUNDLE_REQ" (.getAbsolutePath req-file)}
                                        :expr expr})]
                (when-not (zero? exit)
                          (throw (ex-info "Fresh JVM execution failed"
                                          {:exit   exit
                                           :stdout out
                                           :stderr err})))
                (edn/read-string (clojure.string/trim out)))))