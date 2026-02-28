(ns cloud.code.ns
  (:require [clojure.java.io :as io])
  (:import (java.io PushbackReader StringReader)))

(defn ns->resource-base
      ^String [ns-sym]
      (-> (name ns-sym)
          (clojure.string/replace "." "/")))

(defn find-ns-resource
      [ns-sym]
      (let [base (ns->resource-base ns-sym)]
           (or (io/resource (str base ".clj"))
               (io/resource (str base ".cljc"))
               (io/resource (str base ".cljs")))))

(defn slurp-resource
      ^String [resource-url]
      (with-open [r (io/reader resource-url)]
                 (let [sb (StringBuilder.)]
                      (loop [buf (char-array 4096)]
                            (let [n (.read r buf 0 4096)]
                                 (if (neg? n)
                                   (str sb)
                                   (do
                                     (.append sb buf 0 n)
                                     (recur buf))))))))

(defn read-first-form
      [^String source]
      (let [r (PushbackReader. (StringReader. source))]
           (read {:read-cond :allow
                  :features  #{:clj}} r)))

(defn ns-form?
      [x]
      (and (seq? x) (= 'ns (first x))))

(defn- extract-ns-deps-from-clause
       [clause]
       (let [[kw & specs] clause]
            (when (#{:require :use :require-macros} kw)
                  (->> specs
                       (map (fn [spec]
                                (cond
                                  (symbol? spec) spec
                                  (and (vector? spec) (symbol? (first spec))) (first spec)
                                  :else nil)))
                       (remove nil?)
                       set))))

(defn extract-ns-deps
      [ns-form]
      (when-not (ns-form? ns-form)
                (throw (ex-info "Not an ns form" {:form ns-form})))
      (let [[_ _name & clauses] ns-form]
           (->> clauses
                (filter seq?)
                (mapcat (fn [cl]
                            (or (extract-ns-deps-from-clause cl) #{})))
                set)))

(defn read-ns-info
      [ns-sym]
      (let [res (find-ns-resource ns-sym)]
           (when-not res
                     (throw (ex-info "Namespace source not found on classpath"
                                     {:ns   ns-sym
                                      :hint "Ensure the ns has a .clj/.cljc/.cljs resource on classpath (including inside jars)."})))
           (let [src (slurp-resource res)
                 f (read-first-form src)]
                (when-not (ns-form? f)
                          (throw (ex-info "First form is not (ns ...), can't analyze deps"
                                          {:ns         ns-sym
                                           :resource   res
                                           :first-form f})))
                {:ns       ns-sym
                 :resource res
                 :source   src
                 :deps     (extract-ns-deps f)})))