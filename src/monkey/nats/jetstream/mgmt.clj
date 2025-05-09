(ns monkey.nats.jetstream.mgmt
  "Jetstream management functions"
  (:require [monkey.nats.core :as c])
  (:import [io.nats.client.api StreamConfiguration StreamConfiguration$Builder StorageType]))

(defn make-mgmt
  "Creates management context from Nats connection"
  [conn]
  (.jetStreamManagement conn))

(defn parse-storage-type [t]
  (case t
    :file StorageType/File
    :memory StorageType/Memory))

(defmacro builder-fn [n & args]
  `(memfn ^StreamConfiguration$Builder ~n ~@args))

(def appliers {:name (builder-fn name n)
               :storage-type #(.storageType %1 (parse-storage-type %2))
               :subjects #(.subjects %1 (into-array String %2))})

(defn make-options [conf]
  (-> (StreamConfiguration/builder)
      (c/configure-builder appliers conf)
      (.build)))

(defn add-stream
  "Creates a jetstream using the management and configuration"
  [mgmt conf]
  (.addStream mgmt (make-options conf)))

(defn delete-stream
  "Deletes stream with given name, or name indicated by the stream info."
  [mgmt s]
  (let [n (if (string? s) s (-> s (.getConfiguration) (.getName)))]
    (.deleteStream mgmt n)))
