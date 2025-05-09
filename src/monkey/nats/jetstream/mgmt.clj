(ns monkey.nats.jetstream.mgmt
  "Jetstream management functions"
  (:require [monkey.nats.core :as c])
  (:import [io.nats.client.api
            ConsumerConfiguration ConsumerConfiguration$Builder
            StreamConfiguration StreamConfiguration$Builder StorageType]))

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

(defn stream-name [s]
  (if (string? s) s (-> s (.getConfiguration) (.getName))))

(defn add-stream
  "Creates a jetstream using the management and configuration"
  [mgmt conf]
  (.addStream mgmt (make-options conf)))

(defn delete-stream
  "Deletes stream with given name, or name indicated by the stream info."
  [mgmt s]
  (.deleteStream mgmt (stream-name s)))

(defmacro cons-builder-fn [n & args]
  `(memfn ^StreamConfiguration$Builder ~n ~@args))

(defn consumer-options
  "Creates a `ConsumerConfiguration` from give conf map"
  [conf]
  (let [appliers {:durable (cons-builder-fn durable n)
                  :name (cons-builder-fn name n)
                  :description (cons-builder-fn description d)
                  :filter-subjects (cons-builder-fn filterSubjects l)}]
    (-> (ConsumerConfiguration/builder)
        (c/configure-builder appliers conf)
        (.build))))

(defn make-consumer
  "Creates a consumer for the given jetstream context with the specified options.
   Returns a `MessageConsumer` that can be stopped or closed."
  [js stream opts]
  (.createConsumer js
                   (stream-name stream)
                   (consumer-options opts)))
