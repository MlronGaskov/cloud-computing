(ns cloud.coordinator.state)

(defn now-ms [] (System/currentTimeMillis))

(defn new-state []
  (atom {:workers {}
         :fns     {}
         :queue   clojure.lang.PersistentQueue/EMPTY}))

(defn register-worker!
  [st {:keys [worker-id host port]}]
  (swap! st (fn [s]
              (assoc-in s [:workers worker-id]
                        {:worker-id worker-id
                         :host      host
                         :port      port
                         :busy?     false
                         :last-seen (now-ms)})))
  {:ok true})

(defn heartbeat!
  [st worker-id]
  (swap! st assoc-in [:workers worker-id :last-seen] (now-ms))
  {:ok true})

(defn register-fn!
  [st {:keys [fn-id bundle]}]
  (swap! st assoc-in [:fns fn-id] {:fn-id fn-id :bundle bundle :ts (now-ms)})
  {:ok true})

(defn- worker-url [{:keys [host port]}]
  (str "http://" host ":" port))

(defn- pick-free-worker [workers]
  (->> (vals workers)
       (filter (comp not :busy?))
       (sort-by :last-seen >)
       first))

(defn enqueue!
  [st job]
  (swap! st update :queue conj job))

(defn dequeue!
  [st]
  (let [q (:queue @st)]
    (when-not (empty? q)
      (let [job (peek q)]
        (swap! st update :queue pop)
        job))))

(defn mark-busy!
  [st worker-id busy?]
  (swap! st assoc-in [:workers worker-id :busy?] busy?))

(defn snapshot [st] @st)

(defn choose-worker!
  [st]
  (let [{:keys [workers]} @st]
    (pick-free-worker workers)))

(defn get-fn-bundle
  [st fn-id]
  (get-in @st [:fns fn-id :bundle]))

(defn set-worker-seen!
  [st worker-id]
  (swap! st assoc-in [:workers worker-id :last-seen] (now-ms)))