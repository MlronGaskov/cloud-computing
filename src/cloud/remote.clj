(ns cloud.remote
  (:require [cloud.code.bundle :as bundle]
            [cloud.net.http :as http]
            [cloud.util.log :as log])
  (:import (java.util.concurrent Executors Callable TimeUnit)))

(defonce coord (atom nil))
(defonce registry (atom {}))

(defn connect!
  [{:keys [host port] :or {host "localhost" port 8080}}]
  (reset! coord {:host host :port port})
  (log/info "remote connected" @coord)
  @coord)

(defn coord-url [path]
  (let [{:keys [host port]} @coord]
    (when-not host
      (throw (ex-info "Not connected. Call (cloud.remote/connect!) first." {})))
    (str "http://" host ":" port path)))

(defn register-fn!
  [{:keys [fn-id entry-var]}]
  (let [b (bundle/build-bundle entry-var)
        payload {:fn-id  fn-id
                 :bundle (bundle/transport-bundle b)}]
    (http/post-edn {:url        (coord-url "/register-fn")
                    :body       payload
                    :timeout-ms 60000})))

(defn deploy!
  ([] (deploy! (keys @registry)))
  ([fn-ids]
   (doseq [fn-id fn-ids]
     (let [{:keys [entry-var]} (get @registry fn-id)]
       (when-not entry-var
         (throw (ex-info "Unknown fn-id in registry" {:fn-id fn-id})))
       (log/info "deploy fn" {:fn-id fn-id})
       (register-fn! {:fn-id fn-id :entry-var entry-var})))
   {:ok true :deployed (vec fn-ids)}))

(defn call
  [fn-id & args]
  (http/post-edn {:url        (coord-url "/invoke")
                  :body       {:fn-id fn-id :args args}
                  :timeout-ms 300000}))

(defn seqable-coll?
  [x]
  (and (some? x)
       (or (sequential? x) (set? x) (vector? x) (list? x))
       (not (string? x))
       (not (map? x))))

(defn chunked
  [chunk-size coll]
  (->> coll (partition-all chunk-size) (mapv vec)))

(defn pick-chunk-size
  [n-items workers]
  (let [w (max 1 (long workers))
        target-chunks (* 4 w)
        sz (long (Math/ceil (/ (double n-items) (double target-chunks))))]
    (max 1 sz)))

(defn dmap*
  [{:keys [batch-id workers] :as _opts} items]
  (let [items (vec items)
        w (max 1 (long (or workers 1)))
        chunk-size (pick-chunk-size (count items) w)
        chunks (chunked chunk-size items)
        inflight w
        results (object-array (count chunks))]
    (log/info "dmap" {:batch-id batch-id
                      :items (count items)
                      :chunks (count chunks)
                      :chunk-size chunk-size
                      :inflight inflight})
    (loop [i 0
           running {}]
      (cond
        (and (>= i (count chunks)) (empty? running))
        (->> (range (count chunks))
             (mapcat (fn [idx] (aget results idx)))
             (vec))

        (and (< i (count chunks)) (< (count running) inflight))
        (let [idx i
              chunk (nth chunks idx)
              fut (future
                    (let [resp (call batch-id chunk)]
                      (if (:ok resp)
                        (:result resp)
                        (throw (ex-info "dmap chunk failed" (assoc resp :chunk-idx idx))))))]
          (recur (inc i) (assoc running idx fut)))

        :else
        (let [[done-idx done-fut]
              (first (filter (fn [[_ f]] (realized? f)) running))]
          (if done-idx
            (do
              (aset results done-idx @done-fut)
              (recur i (dissoc running done-idx)))
            (do
              (Thread/sleep 5)
              (recur i running))))))))

(defn batch-local-exec
  [threads f items]
  (let [t (max 1 (long (or threads 1)))
        pool (Executors/newFixedThreadPool t)]
    (try
      (let [tasks (mapv (fn [x]
                          (reify Callable
                            (call [_] (f x))))
                        items)
            futures (.invokeAll pool tasks)
            out (mapv (fn [^java.util.concurrent.Future fu] (.get fu))
                      futures)]
        out)
      (finally
        (.shutdown pool)
        (.awaitTermination pool 5 TimeUnit/SECONDS)))))

(defmacro defremote
  [name opts args & body]
  (let [opts (or opts {})
        fn-id (str (ns-name *ns*) "/" name)
        batch-id (str fn-id "#batch")
        local-sym (symbol (str name "-local"))
        batch-local-sym (symbol (str name "-batch-local"))
        threads (:threads opts)]
    `(do
       (defn ~local-sym ~args ~@body)

       ~@(when (= 1 (count args))
           (let [x# (first args)]
             [`(defn ~batch-local-sym [items#]
                 (cloud.remote/batch-local-exec ~threads
                                                (fn [~x#] (~local-sym ~x#))
                                                items#))]))

       (swap! cloud.remote/registry assoc ~fn-id {:fn-id ~fn-id
                                                  :entry-var (var ~local-sym)
                                                  :opts ~opts})

       ~@(when (= 1 (count args))
           [`(swap! cloud.remote/registry assoc ~batch-id {:fn-id ~batch-id
                                                           :entry-var (var ~batch-local-sym)
                                                           :opts ~opts})])

       (defn ~name ~args
         (let [opts# ~opts]
           (if (and (= 1 (count '~args))
                    (cloud.remote/seqable-coll? ~(first args)))
             (let [items# ~(first args)]
               (cloud.remote/dmap* {:batch-id ~batch-id
                                    :workers  (:workers opts#)}
                                   items#))
             (let [res# (cloud.remote/call ~fn-id ~@args)]
               (if (:ok res#)
                 (:result res#)
                 (throw (ex-info "Remote call failed" res#))))))))))