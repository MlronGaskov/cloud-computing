(ns cloud.remote
  (:require [cloud.code.bundle :as bundle]
            [cloud.net.http :as http]
            [cloud.util.log :as log]))

(defonce coord (atom nil))
(defonce registry (atom {}))

(defn connect!
  [{:keys [host port] :or {host "localhost" port 8080}}]
  (reset! coord {:host host :port port})
  (log/info "remote connected" @coord)
  @coord)

(defn- coord-url [path]
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

(defmacro defremote
  [name args & body]
  (let [fn-id (str (ns-name *ns*) "/" name)
        local-sym (symbol (str name "-local"))]
    `(do
       (defn ~local-sym ~args ~@body)
       (swap! registry assoc ~fn-id {:fn-id ~fn-id :entry-var (var ~local-sym)})
       (defn ~name ~args
         (let [res# (apply call ~fn-id ~args)]
           (if (:ok res#)
             (:result res#)
             (throw (ex-info "Remote call failed" res#))))))))