(ns monkey.nats.test-helpers
  (:require [babashka.fs :as fs]
            [config.core :as cc]
            [monkey.nats.core :as c]))

(def url (:nats-url cc/env))
(def creds (:nats-creds cc/env))
(def stream (:nats-stream cc/env))

(defn add-creds [conf]
  (let [k (if (fs/exists? creds) :credential-path :static-creds)]
    (assoc conf k creds)))

(defn wait-until [pred timeout rv]
  (let [start (System/currentTimeMillis)]
    (loop []
      (if-let [v (pred)]
        v
        (if (> (- (System/currentTimeMillis) start) timeout)
          rv
          (do
            (Thread/sleep 100)
            (recur)))))))

(defn make-connection []
  (c/make-connection (-> {:urls [url]
                          :secure? true
                          :verbose? true}
                         (add-creds))))
