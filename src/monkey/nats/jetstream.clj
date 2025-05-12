(ns monkey.nats.jetstream
  "Functions for working with Nats JetStreams"
  (:require [monkey.nats.core :as c]
            [monkey.nats.jetstream.mgmt :as m])
  (:import [io.nats.client FetchConsumer FetchConsumeOptions FetchConsumeOptions$Builder]))

(defn make-jetstream
  "Gets jetstream context from the NATS connection"
  [conn]
  (.jetStream conn))

(defn consumer-ctx
  "Creates a new consumer context for the stream, with given consumer id. 
   This can then be used to `consume` or `fetch`."
  [js stream id]
  (.getConsumerContext js (m/stream-name stream) id))

(defn consume
  "Starts consuming from the context, where messages are passed to
   the given handler.  Returns a `MessageConsumer` that can be `stop`ped
   or `close`d.  Depending on the consumer configuration `ack-policy`, 
   messages may need to be explicitly `ack`ed."
  [ctx handler]
  (.consume ctx (c/->message-handler handler)))

(defn stop
  "Stops consuming from the given consumer.  Use `close` to complete cleanup
   and unsubscribe."
  [c]
  (.stop c))

(defn close [c]
  (.close c))

(defmacro fetch-builder-fn [n & args]
  `(memfn ^FetchConsumeOptions$Builder ~n ~@args))

(defn fetch-options [conf]
  (let [appliers {:no-wait (fn [c v]
                             (cond-> c
                               v (.noWait c)))
                  :max-bytes (fetch-builder-fn maxBytes n)
                  :max-messages (fetch-builder-fn maxMessages n)
                  :expires-in (fetch-builder-fn expiresIn millis)}]
    (-> (FetchConsumeOptions/builder)
        (c/configure-builder appliers conf)
        (.build))))

(defn fetch
  "Creates a fetcher for the consumercontext.  It allows the caller to explicitly
   retrieve the next message, instead of using a handler like with `consume`.
   The return value can be called as a function, or used as a `FetchConsumer`"
  [ctx conf]
  (let [f (.fetch ctx (fetch-options conf))]
    (reify
      clojure.lang.IFn
      (invoke [_]
        (.nextMessage f))
      FetchConsumer
      (nextMessage [_]
        (.nextMessage f))
      (close [_]
        (.close f)))))

(def ack
  "Acknowledges message, if `ack-policy` is `explicit` or `all`."
  (memfn ack))

(defn publish
  "Publishes message to the given subject using JetStream.  In the options
   a custom `serializer` can be configured.  By default this is `core/to-edn`."
  [js subj msg {:keys [serializer] :or {serializer c/to-edn}}]
  (.publish js subj (serializer msg)))
