(ns cloud.coordinator.http
  (:require [cloud.coordinator.state :as state]
            [cloud.net.http :as http]
            [cloud.util.log :as log]))

(defn- task-payload [bundle args]
  {:bundle  bundle
   :invoke? true
   :args    args})

(defn- dispatch-job!
  [st job]
  (let [{:keys [promise fn-id args]} job
        worker (state/choose-worker! st)]
    (if-not worker
      (do
        (state/enqueue! st job)
        :queued)
      (let [worker-id (:worker-id worker)]
        (state/mark-busy! st worker-id true)
        (future
          (try
            (state/set-worker-seen! st worker-id)
            (let [bundle (state/get-fn-bundle st fn-id)
                  resp (http/post-edn {:url        (str "http://" (:host worker) ":" (:port worker) "/task")
                                       :body       {:bundle bundle :args args}
                                       :timeout-ms 300000})]
              (deliver promise resp))
            (catch Throwable t
              (deliver promise {:ok false :error (str t)}))
            (finally
              (state/mark-busy! st worker-id false)
              (loop []
                (when-let [next (state/dequeue! st)]
                  (if (= :queued (dispatch-job! st next))
                    (state/enqueue! st next)
                    (recur)))))))
        :sent))))

(defn routes
  [st]
  {"/register-worker"
   (fn [{:keys [req]}]
     (let [{:keys [worker-id host port]} req]
       (log/info "register-worker" req)
       (state/register-worker! st {:worker-id worker-id :host host :port port})))

   "/heartbeat"
   (fn [{:keys [req]}]
     (let [{:keys [worker-id]} req]
       (state/heartbeat! st worker-id)))

   "/register-fn"
   (fn [{:keys [req]}]
     (let [{:keys [fn-id bundle]} req]
       (log/info "register-fn" {:fn-id fn-id :nss (count (:namespaces bundle))})
       (state/register-fn! st {:fn-id fn-id :bundle bundle})))

   "/invoke"
   (fn [{:keys [req]}]
     (let [{:keys [fn-id args]} req
           p (promise)
           job {:fn-id fn-id :args args :promise p}]
       (when-not (state/get-fn-bundle st fn-id)
         {:ok false :error (str "Unknown fn-id: " fn-id)})
       (dispatch-job! st job)
       @p))

   "/status"
   (fn [_]
     (let [s (state/snapshot st)]
       {:ok         true
        :workers    (->> (vals (:workers s))
                         (map #(select-keys % [:worker-id :host :port :busy? :last-seen]))
                         (sort-by :worker-id))
        :fns        (->> (keys (:fns s)) sort vec)
        :queue-size (count (:queue s))}))})