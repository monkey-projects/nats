(ns user
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [monkey.nats
             [core :as c]
             [jetstream :as js]])
  (:import java.io.PushbackReader))

(defn load-config []
  (with-open [r (PushbackReader. (io/reader "dev-resources/config.edn"))]
    (edn/read r)))

(defn connect []
  (let [conf (load-config)]
    (c/make-connection {:urls [(:nats-url conf)]
                        :credential-path (:nats-creds conf)
                        :secure? true})))

(def jetstream js/make-jetstream)
(def consumer-ctx js/consumer-ctx)
