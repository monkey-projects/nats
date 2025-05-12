# Clojure Nats

Clojure library for [NATS messaging](https://nats.io), built on top of the
[Java Nats implementation](https://github.com/nats-io/nats.java).

# Usage

[![Clojars Project](https://img.shields.io/clojars/v/com.monkeyprojects/nats.svg)](https://clojars.org/com.monkeyprojects/nats)

Include the library in your project:
```clojure
;; Leiningen
[com.monkeyprojects/nats "<version>"]
```
Or, with Clojure CLI:
```clojure
;; deps.edn
com.monkeyprojects/nats {:mvn/version "<version>"}
```

After that, you can start using it by creating a connection to a remote NATS server.
```clojure
(require '[monkey.nats.core :as nc])

(def conn (nc/make-connection {:urls ["nats-server:1234"]    ; One or more urls to connect to
                               :secure? true                 ; Use TLS
			       :token "very-secure-token"})) ; See authentication below
```

The `monkey.nats.core` namespace provides basic functionality for publishing or
consuming messages.
```clojure
;; Subscribe to test.subject, and just print the messages
(def sub (nc/subscribe conn "test.subject" println {}))

;; Publish a message
(nc/publish conn "test.subject" {:message "This is a test"} {})
;; => Will print the message to console
```

## Serialization

By default, the messages are serialized into [edn](https://github.com/edn-format/edn).
You can customize this by specifying a `serializer` in the `publish` options, and a
similar `dezerializer` when subscribing.  Note that the serializer should create a
`byte[]` array, wherease the deserializer should handle complete NATS `Message`s.
This is because the underlying library disencourages implementing `Messages` but
instead provides a bunch of utility function.

For example, this is how Json serialization could look like, using
[Cheshire](https://github.com/dakrone/cheshire):
```clojure
(require '[cheshire.core :as json])

(defn to-json [msg]
  (-> (json/generate-string msg)  
      (.getBytes "UTF-8)))  ; Must return a byte array

(defn from-json [nats-msg]
  (-> nats-msg
      (.getData)
      (String.)
      (json/parse-string)))

(def subs (nc/subscribe conn "test.subject" println {:deserializer from-json}))

(nc/publish conn "test.subject" {:message "Json message"} {:serializer to-json})
```

## Authentication

This library supports various methods of authentication.

  - Tokens: pass the `:token` property in the connection options
  - `:credential-path`: points to a path that contains credentials
  - `:static-creds`: provide credentials from a static string
  - `:auth-handler`: a custom [AuthHandler](https://javadoc.io/static/io.nats/jnats/2.21.1/io/nats/client/AuthHandler.html)

It's up to you to decide which suits your needs better.

## Queues

In order to ensure that one message is only passed to one subscriber, instead of all
of them, use a [queue group](https://docs.nats.io/nats-concepts/core-nats/queue).  In
order to do this, pass the `:queue` option when subscribing:

```clojure
(def sub (nc/subscribe conn "test.subject" println {:queue "my-queue"}))
```

Multiple subscribers that use the same queue name, will only process each message once.

# JetStream

The above pub/sub pattern has a big drawback: if no subscriptions are listening
on a subject, the messages are lost.  In order to have some persistence, similar to
Kafka topics or JMS durable subscribers, you need to use
[JetStream](https://docs.nats.io/nats-concepts/jetstream).  It is beyond the scope
of this documentation to explain what it is, but you can read about it in the [official
Nats documentation](https://docs.nats.io/nats-concepts/jetstream).  We do provide
a wrapper for JetStream.  It consists of two namespaces: one to manage streams
(`monkey.nats.jetstream.mgmt`) and another on to consume from/publish to streams
(`monkey.nats.jetstream`).

```clojure
(require '[monkey.nats.jetstream :as js])
(require '[monkey.nats.jetstream.mgmt :as mgmt])

(def mgmt-ctx (mgmt/make-management conn)

;; Configure a stream to store messages
(def stream (mgmt/add-stream {:name "test-stream"
                              :subjects ["test.subject"]
			      :storage-type :file}))
;; Set up a consumer to pull messages
(def consumer (mgmt/make-consumer mgmt stream {:durable "my-consumer-id"}))

;; Create jetstream context for further use
(def js (js/make-jetstream conn))
;; Create a consumption context and start consuming
(def ctx (js/consumer-ctx js "test-stream" "my-consumer-id"))
(def msg-cons (js/consume ctx println {}))

;; Publish a message
(js/publish js "test.subject" {:message "Jetstream message"} {})
;; => Will print the message using above consumer

;; Stop consuming
(js/stop msg-cons)
(js/close msg-cons)

;; You can also manually fetch messages
(def fetcher (js/fetch ctx {:expires-in 1000}))
(js/publish js "test.subject" {:message "Another Jetstream message"} {})
;; The fetcher acts as a regular 0-arity function
(fetcher)
;; => {:message "Another Jetstream message"}

;; Clean up
(mgmt/delete-stream stream)
```

Again, serialization is done using `edn`.  You can specify your own serializer and
deserializer, similar to the basic pub/sub functions.

## Acknowledging Messages

If you have passed `:ack-policy` `:all` or `:explicit` as an option to
`mgmt/make-consumer`, you will have to manually acknowledge each message, to
indicate to the consumer that the message can be removed from the persistent
store.  In order to do this, just call `ack` on the message.  Note that your
`deserializer` will need to pass the entire message to your handler in this
case (possibly along with other information).

```clojure
(defn custom-deserializer [msg]
  {:orig msg
   :payload (nc/from-edn msg)})

(handler [msg]
  (println "The payload is:" (:payload msg))
  (js/ack (:orig msg)))

(def msg-cons (js/consume ctx handler {:deserializer custom-deserializer}))
```

# License

[MIT License](LICENSE)

Copyright (c) 2025 by [Monkey Projects BV](https://www.monkey-projects.be)
