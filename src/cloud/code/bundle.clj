(ns cloud.code.bundle
  (:require [cloud.code.ns :as nsu])
  (:import (clojure.lang PersistentQueue Var)))

(defn- normalize-entry
       [entry]
       (cond
         (instance? Var entry)
         (let [^Var v entry
               ns-sym (-> v .ns ns-name symbol)
               sym (-> v .sym)]
              {:ns ns-sym :sym sym :var v})

         (and (symbol? entry) (namespace entry))
         (let [ns-sym (symbol (namespace entry))
               sym (symbol (name entry))]
              {:ns ns-sym :sym sym :var nil})

         :else
         (throw (ex-info "Entry must be a Var or a qualified symbol (ns/sym)"
                         {:entry entry}))))

(defn- bfs-closure
       [root-ns]
       (loop [q (conj PersistentQueue/EMPTY root-ns)
              seen #{}
              acc {}]
             (if (empty? q)
               acc
               (let [ns-sym (peek q)
                     q' (pop q)]
                    (if (contains? seen ns-sym)
                      (recur q' seen acc)
                      (let [info (nsu/read-ns-info ns-sym)
                            deps (:deps info)
                            q'' (reduce conj q' deps)]
                           (recur q''
                                  (conj seen ns-sym)
                                  (assoc acc ns-sym info))))))))

(defn- topo-order
       [ns->info]
       (let [deps (into {} (map (fn [[k v]] [k (:deps v)]) ns->info))
             state (atom {:temp #{} :perm #{} :out []})]
            (letfn [(visit! [n]
                            (let [{:keys [temp perm]} @state]
                                 (cond
                                   (contains? perm n) nil
                                   (contains? temp n)
                                   (throw (ex-info "Cyclic dependency detected in ns graph"
                                                   {:cycle-at n}))
                                   :else
                                   (do
                                     (swap! state update :temp conj n)
                                     (doseq [d (get deps n)]
                                            (when (contains? ns->info d)
                                                  (visit! d)))
                                     (swap! state (fn [st]
                                                      (-> st
                                                          (update :temp disj n)
                                                          (update :perm conj n)
                                                          (update :out conj n))))))))]
                   (doseq [n (keys ns->info)]
                          (visit! n))
                   (vec (:out @state)))))

(defn build-bundle
      [entry]
      (let [{:keys [ns sym]} (normalize-entry entry)
            ns->info (bfs-closure ns)
            order (topo-order ns->info)]
           {:entry      (symbol (name ns) (name sym))
            :root-ns    ns
            :namespaces (into {}
                              (map (fn [[k v]]
                                       [k (select-keys v [:source :deps :resource])])
                                   ns->info))
            :load-order order}))

(defn transport-bundle
      [bundle]
      (-> bundle
          (update :namespaces
                  (fn [m]
                      (into {}
                            (map (fn [[ns-sym info]]
                                     [ns-sym (dissoc info :resource)]))
                            m)))))