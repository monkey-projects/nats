(ns monkey.nats.jetstream
  "Functions for working with Nats JetStreams"
  (:require [monkey.nats
             [core :as c]
             [utils :as u]]
            [monkey.nats.jetstream.mgmt :as m])
  (:import [io.nats.client
            FetchConsumer FetchConsumeOptions ConsumeOptions]))

(defn make-jetstream
  "Gets jetstream context from the NATS connection"
  ^io.nats.client.JetStream [^io.nats.client.Connection conn]
  (.jetStream conn))

(defn consumer-ctx
  "Creates a new consumer context for the stream, with given consumer id. 
   This can then be used to `consume` or `fetch`."
  [^io.nats.client.JetStream js ^String stream ^String id]
  (.getConsumerContext js (m/stream-name stream) id))

(defn consume-options ^ConsumeOptions [conf]
  (u/configure-builder (ConsumeOptions/builder) conf))

(defn consume
  "Starts consuming from the context, where messages are passed to
   the given handler.  Returns a `MessageConsumer` that can be `stop`ped
   or `close`d.  Depending on the consumer configuration `ack-policy`, 
   messages may need to be explicitly `ack`ed."
  ([^io.nats.client.ConsumerContext ctx handler {:keys [deserializer] :as conf}]
   (.consume ctx
             (consume-options (dissoc conf :deserializer))
             (c/->message-handler (cond-> handler
                                    deserializer (comp deserializer)))))
  ([ctx handler]
   (consume ctx handler {})))

(defn stop
  "Stops consuming from the given consumer.  Use `close` to complete cleanup
   and unsubscribe."
  [^io.nats.client.MessageConsumer c]
  (.stop c))

(defn close [^java.lang.AutoCloseable c]
  (.close c))

(defn fetch-options [conf]
  (u/configure-builder (FetchConsumeOptions/builder) conf))

(defn fetch
  "Creates a fetcher for the consumercontext.  It allows the caller to explicitly
   retrieve the next message, instead of using a handler like with `consume`.
   The return value can be called as a function, or used as a `FetchConsumer`"
  [^io.nats.client.ConsumerContext ctx {:keys [deserializer] :as conf}]
  (let [f (.fetch ctx (-> conf
                          (dissoc :deserializer)
                          (fetch-options)))]
    (reify
      clojure.lang.IFn
      (invoke [this]
        (.nextMessage this))
      FetchConsumer
      (nextMessage [_]
        (cond-> (.nextMessage f)
          deserializer (deserializer)))
      (close [_]
        (.close f)))))

(def ack
  "Acknowledges message, if `ack-policy` is `explicit` or `all`."
  (memfn ^io.nats.client.Message ack))

(defn publish
  "Publishes message to the given subject using JetStream.  In the options
   a custom `serializer` can be configured.  By default this is `core/to-edn`."
  [^io.nats.client.JetStream js ^String subj msg {:keys [serializer] :or {serializer c/to-edn}}]
  (.publish js subj ^byte/1 (serializer msg)))
