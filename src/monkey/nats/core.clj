(ns monkey.nats.core
  "Core namespace that provides a layer on top of the Java Nats library functionality,
   or at least part of it."
  (:require [clojure
             [edn :as edn]
             [string :as str]]
            [clojure.java.io :as io]
            [clojure.tools.logging :as log])
  (:import [io.nats.client Connection MessageHandler Nats Options$Builder]
           java.io.PushbackReader))

(def connection? (partial instance? Connection))

(defn configure-builder [builder appliers conf]
  (reduce-kv (fn [o k v]
               (let [a (get appliers k)]
                 (cond-> o
                   a (a v))))
             builder
             conf))

(defn- apply-conf [opts conf]
  (let [appliers {:urls #(.servers %1 (into-array String %2))
                  :secure? (fn [o t?] (cond-> o
                                        t? (.secure)))
                  :token #(.token %1 (.toCharArray %2))
                  :auth-handler #(.authHandler %1 %2)
                  :credential-path #(.credentialPath %1 %2)
                  :static-creds #(.authHandler %1 (Nats/staticCredentials (.getBytes %2)))
                  :verbose? (fn [o t?] (cond-> o
                                         t? (.verbose)))}]
    (configure-builder opts appliers conf)))

(defn make-options
  "Creates a Nats options object using the given configuration"
  [conf]
  (-> (Options$Builder.)
      (apply-conf conf)
      (.build)))

(defn ^Connection make-connection
  "Creates a Nats connection, returning a Nats object.  Opts is a map containing
   the `:urls` and possible other values."
  [opts]
  (Nats/connect (make-options opts)))

(defn to-bytes [s]
  (.getBytes s "UTF-8"))

(defn to-edn
  "Converts given object into edn byte array"
  [obj]
  (-> obj
      (pr-str)
      (to-bytes)))

(defn from-edn
  "Parses given message data from edn"
  [msg]
  (with-open [r (PushbackReader. (io/reader (.getData msg)))]
    (edn/read r)))

(defn ->message-handler [f]
  (reify MessageHandler
    (onMessage [this msg]
      (f msg))))

(defn subscribe
  "Creates a subscription for the given subject, which invokes `handler` on each
   received message.  Returns a subscription that can be passed to `unsubscribe`.
   An `queue` and `deserializer` fn can be passed to the options."
  [conn subject handler {:keys [queue deserializer]}]
  (let [h (->message-handler (cond-> handler
                               deserializer (comp deserializer)))]
    (cond-> (.createDispatcher conn)
      queue (.subscribe subject queue h)
      (not queue) (.subscribe subject h))))

(defn unsubscribe
  "Unsubscribes the subscription from its dispatcher"
  [s]
  (.unsubscribe (.getDispatcher s) s))

(defn publish
  "Publishes the message to given subject.  Options map accepts a `serializer`
   fn and a `reply-to` subject."
  [conn subject msg {:keys [serializer reply-to] :or {serializer to-edn}}]
  (if reply-to
    (.publish conn subject reply-to (serializer msg))
    (.publish conn subject (serializer msg))))
