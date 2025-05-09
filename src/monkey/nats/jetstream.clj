(ns monkey.nats.jetstream
  "Functions for working with Nats JetStreams"
  (:require [monkey.nats.core :as c]))

(defn make-jetstream
  "Gets jetstream context from the NATS connection"
  [conn]
  (.jetStream conn))
