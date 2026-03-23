(ns cloud.java.stream
  (:import (java.util ArrayList Collection)
           (java.util.stream Stream)
           (java.util.function Function Predicate BinaryOperator)))

(defn stream-of [source]
  {:source source
   :ops []
   :terminal nil})

(defn map-op [plan f]
  (update plan :ops conj {:type :map :f f}))

(defn filter-op [plan p]
  (update plan :ops conj {:type :filter :p p}))

(defn reduce-op [plan identity op]
  (assoc plan :terminal {:type :reduce :identity identity :op op}))

(defn collect-to-list [plan]
  (assoc plan :terminal {:type :collect-to-list}))

(defn- source->stream ^Stream [source]
  (cond
    (instance? Stream source) source
    (instance? Collection source) (.stream ^Collection source)
    (sequential? source) (.stream ^Collection (ArrayList. source))
    :else (throw (ex-info "Unsupported stream source" {:source (class source)}))))

(defn execute [plan]
  (let [s0 (source->stream (:source plan))
        s1 (reduce (fn [^Stream s op]
                     (case (:type op)
                       :map (.map s ^Function (:f op))
                       :filter (.filter s ^Predicate (:p op))
                       (throw (ex-info "Unknown stream op" {:op op}))))
                   s0
                   (:ops plan))
        term (:terminal plan)]
    (case (:type term)
      :reduce (.reduce s1 (:identity term) ^BinaryOperator (:op term))
      :collect-to-list (.toList s1)
      (throw (ex-info "Unknown terminal op" {:terminal term})))))