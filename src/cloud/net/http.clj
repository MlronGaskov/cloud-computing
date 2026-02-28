(ns cloud.net.http
  (:require [clojure.java.io :as io]
            [cloud.net.codec :as codec]
            [cloud.util.log :as log])
  (:import (com.sun.net.httpserver HttpExchange HttpHandler HttpServer)
           (java.net InetSocketAddress URI)
           (java.net.http HttpClient HttpRequest HttpRequest$BodyPublishers HttpResponse$BodyHandlers)
           (java.time Duration)
           (java.util.concurrent Executors)))

(defn- read-body ^String [^HttpExchange ex]
  (slurp (io/reader (.getRequestBody ex))))

(defn- write-body! [^HttpExchange ex status ^String body]
  (let [bytes (.getBytes body "UTF-8")]
    (.getResponseHeaders ex)
    (.add (.getResponseHeaders ex) "Content-Type" "application/edn; charset=utf-8")
    (.sendResponseHeaders ex status (long (alength bytes)))
    (with-open [os (.getResponseBody ex)]
      (.write os bytes))))

(defn- method [^HttpExchange ex]
  (keyword (.toLowerCase (.getRequestMethod ex))))

(defn handler
  [f]
  (reify HttpHandler
    (handle [_ ex]
      (try
        (let [m (method ex)
              req (when (#{:post :put} m)
                    (codec/decode (read-body ex)))
              res (f {:method      m
                      :path        (.getPath (.getRequestURI ex))
                      :req         req
                      :remote-addr (str (.getRemoteAddress ex))})]
          (write-body! ex 200 (codec/encode res)))
        (catch Throwable t
          (log/error "http handler error" {:ex (str t)})
          (write-body! ex 500 (codec/encode {:ok false :error (str t)})))))))

(defn start-server!
  [{:keys [host port routes]}]
  (let [addr (InetSocketAddress. (or host "0.0.0.0") (int port))
        ^HttpServer s (HttpServer/create addr 0)]
    (doseq [[path f] routes]
      (.createContext s path (handler f)))
    (.setExecutor s (Executors/newCachedThreadPool))
    (.start s)
    (log/info "http server started" {:host host :port port :routes (sort (keys routes))})
    s))

(defn stop-server! [^HttpServer s]
  (when s
    (.stop s 0)
    (log/info "http server stopped")))

(def ^:private http-client
  (-> (HttpClient/newBuilder)
      (.connectTimeout (Duration/ofSeconds 5))
      (.build)))

(defn post-edn
  [{:keys [url body timeout-ms]}]
  (let [req (-> (HttpRequest/newBuilder (URI/create url))
                (.timeout (Duration/ofMillis (long (or timeout-ms 60000))))
                (.header "Content-Type" "application/edn; charset=utf-8")
                (.POST (HttpRequest$BodyPublishers/ofString (codec/encode body)))
                (.build))
        resp (.send http-client req (HttpResponse$BodyHandlers/ofString))]
    (codec/decode (.body resp))))