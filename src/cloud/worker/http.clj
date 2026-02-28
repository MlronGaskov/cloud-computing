(ns cloud.worker.http
  (:require [cloud.util.log :as log]
            [cloud.worker.runtime :as rt]))

(defn routes []
  {"/task"
   (fn [{:keys [req]}]
     (try
       (let [res (rt/execute! req)]
         {:ok true :result res})
       (catch Throwable t
         (log/error "task failed" {:ex (str t)})
         {:ok false :error (str t)})))})
