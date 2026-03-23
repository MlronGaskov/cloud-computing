(ns cloud.net.codec
  (:require [clojure.string :as str])
  (:import (java.io ByteArrayInputStream
                    ByteArrayOutputStream
                    DataInputStream
                    DataOutputStream
                    File
                    FileOutputStream
                    ObjectInputStream
                    ObjectOutputStream
                    ObjectStreamClass)
           (java.lang.reflect Array Field Modifier)
           (java.net URL URLClassLoader)
           (java.nio.charset StandardCharsets)
           (java.nio.file Files)
           (java.util Base64 Collections IdentityHashMap)
           (java.util.jar JarEntry JarOutputStream)))

(defn with-context-classloader [^ClassLoader loader f]
  (let [t (Thread/currentThread)
        prev (.getContextClassLoader t)]
    (try
      (.setContextClassLoader t loader)
      (f)
      (finally
        (.setContextClassLoader t prev)))))

(defn- class-code-source-url [^Class cls]
  (some-> cls .getProtectionDomain .getCodeSource .getLocation))

(defn- file-url? [^URL url]
  (= "file" (.getProtocol url)))

(defn- url->file ^File [^URL url]
  (File. (.toURI url)))

(defn- zip-dir-to-bytes [^File dir]
  (let [baos (ByteArrayOutputStream.)]
    (with-open [zos (JarOutputStream. baos)]
      (let [root-path (.toPath dir)]
        (doseq [f (file-seq dir)
                :when (.isFile ^File f)]
          (let [rel (.toString (.normalize (.relativize root-path (.toPath f))))
                entry (JarEntry. (str/replace rel "\\" "/"))]
            (.putNextEntry zos entry)
            (.write zos (Files/readAllBytes (.toPath f)))
            (.closeEntry zos)))))
    (.toByteArray baos)))

(defn- jar-or-dir-entry [^Class cls]
  (when-let [url (class-code-source-url cls)]
    (when (file-url? url)
      (let [f (url->file url)]
        (when (.exists f)
          (if (.isDirectory f)
            {:id (.getAbsolutePath f)
             :name (str (.getName f) ".jar")
             :bytes (zip-dir-to-bytes f)}
            {:id (.getAbsolutePath f)
             :name (.getName f)
             :bytes (Files/readAllBytes (.toPath f))}))))))

(defn- primitiveish? [x]
  (or (nil? x)
      (string? x)
      (number? x)
      (char? x)
      (boolean? x)
      (keyword? x)
      (symbol? x)
      (uuid? x)
      (instance? java.time.Instant x)
      (instance? java.util.Date x)))

(defn- clojure-fn-object? [x]
  (or (fn? x)
      (instance? clojure.lang.AFunction x)
      (instance? clojure.lang.MultiFn x)))

(defn- visit-object!
  [x seen jars]
  (when (and (some? x) (not (primitiveish? x)) (not (.contains seen x)))
    (when (clojure-fn-object? x)
      (throw (ex-info "Passing raw Clojure functions/closures as payload is not supported"
                      {:class (class x)})))
    (.add seen x)
    (let [cls (.getClass x)]
      (when-not (.isArray cls)
        (when-let [entry (jar-or-dir-entry cls)]
          (swap! jars #(if (contains? % (:id entry)) % (assoc % (:id entry) entry)))))
      (cond
        (map? x)
        (doseq [[k v] x]
          (visit-object! k seen jars)
          (visit-object! v seen jars))

        (instance? java.util.Map x)
        (doseq [e (.entrySet ^java.util.Map x)]
          (visit-object! (.getKey ^java.util.Map$Entry e) seen jars)
          (visit-object! (.getValue ^java.util.Map$Entry e) seen jars))

        (or (sequential? x) (set? x))
        (doseq [e x]
          (visit-object! e seen jars))

        (instance? java.lang.Iterable x)
        (doseq [e x]
          (visit-object! e seen jars))

        (.isArray cls)
        (let [n (Array/getLength x)]
          (dotimes [i n]
            (visit-object! (Array/get x i) seen jars)))

        :else
        (doseq [^Field f (.getDeclaredFields cls)]
          (when-not (Modifier/isStatic (.getModifiers f))
            (try
              (.setAccessible f true)
              (visit-object! (.get f x) seen jars)
              (catch Throwable _ nil))))))))

(defn- collect-artifacts [payload]
  (let [seen (Collections/newSetFromMap (IdentityHashMap.))
        jars (atom {})]
    (visit-object! payload seen jars)
    (->> @jars vals vec)))

(defn- write-artifacts! [^DataOutputStream dos artifacts]
  (.writeInt dos (count artifacts))
  (doseq [{:keys [name bytes]} artifacts]
    (let [name-bytes (.getBytes ^String name StandardCharsets/UTF_8)]
      (.writeInt dos (alength name-bytes))
      (.write dos name-bytes)
      (.writeInt dos (alength ^bytes bytes))
      (.write dos ^bytes bytes))))

(defn- read-artifacts! [^DataInputStream dis]
  (let [n (.readInt dis)]
    (vec
     (for [_ (range n)]
       (let [name-len (.readInt dis)
             name-bytes (byte-array name-len)
             _ (.readFully dis name-bytes)
             data-len (.readInt dis)
             data (byte-array data-len)
             _ (.readFully dis data)]
         {:name (String. name-bytes StandardCharsets/UTF_8)
          :bytes data})))))

(defn- write-object-bytes [payload]
  (let [baos (ByteArrayOutputStream.)]
    (with-open [oos (ObjectOutputStream. baos)]
      (.writeObject oos payload))
    (.toByteArray baos)))

(defn- materialize-loader ^ClassLoader [artifacts]
  (if (empty? artifacts)
    (.getContextClassLoader (Thread/currentThread))
    (let [tmp-dir (doto (File/createTempFile "cloud-artifacts-" "")
                    (.delete)
                    (.mkdirs))
          urls (into-array
                URL
                (for [{:keys [name bytes]} artifacts]
                  (let [f (File. tmp-dir name)]
                    (with-open [fos (FileOutputStream. f)]
                      (.write fos ^bytes bytes))
                    (.deleteOnExit f)
                    (.toURL (.toURI f)))))]
      (URLClassLoader. urls (.getContextClassLoader (Thread/currentThread))))))

(defn- read-object-bytes [payload-bytes ^ClassLoader loader]
  (with-context-classloader
    loader
    (fn []
      (with-open [ois (proxy [ObjectInputStream] [(ByteArrayInputStream. payload-bytes)]
                        (resolveClass [^ObjectStreamClass desc]
                          (try
                            (Class/forName (.getName desc) false loader)
                            (catch Throwable _
                              (proxy-super resolveClass desc))))
                        (resolveProxyClass [ifaces]
                          (let [classes (into-array Class
                                                    (map #(Class/forName % false loader) ifaces))]
                            (java.lang.reflect.Proxy/getProxyClass loader classes))))]
        (.readObject ois)))))

(defn encode [payload]
  (let [artifacts (collect-artifacts payload)
        payload-bytes (write-object-bytes payload)
        baos (ByteArrayOutputStream.)]
    (with-open [dos (DataOutputStream. baos)]
      (.writeInt dos 1)
      (write-artifacts! dos artifacts)
      (.writeInt dos (alength payload-bytes))
      (.write dos payload-bytes))
    (.encodeToString (Base64/getEncoder) (.toByteArray baos))))

(defn decode-with-loader [^String s]
  (let [wire (.decode (Base64/getDecoder) s)]
    (with-open [dis (DataInputStream. (ByteArrayInputStream. wire))]
      (let [version (.readInt dis)
            _ (when-not (= 1 version)
                (throw (ex-info "Unsupported wire version" {:version version})))
            artifacts (read-artifacts! dis)
            payload-len (.readInt dis)
            payload-bytes (byte-array payload-len)
            _ (.readFully dis payload-bytes)
            loader (materialize-loader artifacts)
            value (read-object-bytes payload-bytes loader)]
        {:value value
         :loader loader}))))

(defn decode [^String s]
  (:value (decode-with-loader s)))