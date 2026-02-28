(ns cloud.code.loader
    (:require [cloud.code.ns :as nsu]))

(defn- ns->load-path
       ^String [ns-sym]
       (nsu/ns->resource-base ns-sym))

(defn load-bundle!
      ([bundle]
       (load-bundle! bundle nil))
      ([bundle {:keys [invoke? args] :or {invoke? false args []}}]
       (let [ns->payload (:namespaces bundle)
             path->src (into {}
                             (map (fn [[ns-sym {:keys [source]}]]
                                      [(ns->load-path ns-sym) source])
                                  ns->payload))
             loaded (atom #{})
             orig-load clojure.core/load]
            (with-redefs [clojure.core/load
                          (fn [& paths]
                              (doseq [p paths]
                                     (let [p (str p)]
                                          (when-not (contains? @loaded p)
                                                    (if-let [src (get path->src p)]
                                                            (do
                                                              (swap! loaded conj p)
                                                              (load-string src))
                                                            (orig-load p)))))
                              true)]
                         (doseq [ns-sym (:load-order bundle)]
                                (let [p (ns->load-path ns-sym)]
                                     (when-not (contains? @loaded p)
                                               (when-let [src (get path->src p)]
                                                         (swap! loaded conj p)
                                                         (load-string src)))))
                         (if invoke?
                           (let [entry (:entry bundle)
                                 v (resolve entry)]
                                (when-not (var? v)
                                          (throw (ex-info "Entry var not resolved after bundle load"
                                                          {:entry    entry
                                                           :resolved v})))
                                (apply @v args))
                           :loaded)))))