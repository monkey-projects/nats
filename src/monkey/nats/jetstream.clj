(ns monkey.nats.jetstream
  "Functions for working with Nats JetStreams")

(defn make-jetstream
  "Gets jetstream context from the NATS connection"
  [conn]
  (.jetStream conn))

(defn make-consumer
  "Creates a consumer for the given jetstream context with the specified options.
   Returns a `MessageConsumer` that can be stopped or closed."
  [js opts])
