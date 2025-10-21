(ns monkey.nats.core-test
  (:require [clojure
             [edn :as edn]
             [test :refer [deftest is testing]]]
            [monkey.nats
             [core :as sut]
             [test-helpers :as h]])
  (:import (io.nats.client Message ErrorListener ErrorListener$FlowControlSource)))

(deftest make-options
  (testing "passes urls from map"
    (let [urls ["nats://test-url:4222"]]
      (is (= urls
             (->> (sut/make-options {:urls urls})
                  (.getServers)
                  (map str))))))

  (testing "accepts static credentials"
    (is (some? (-> (sut/make-options {:static-creds "test-creds"})
                   (.getAuthHandler)))))

  (testing "accepts file credentials"
    (is (some? (-> (sut/make-options {:credential-path "test-path"})
                   (.getAuthHandler)))))

  (testing "accepts error listener"
    (let [l (reify ErrorListener)]
      (is (= l (-> (sut/make-options {:error-listener l})
                   (.getErrorListener)))))))

(deftest ->error-listener
  (let [recv (atom nil)
        l (sut/->error-listener (partial reset! recv))
        conn (reify io.nats.client.Connection)]
    (testing "wraps function in error listener class"
      (is (instance? io.nats.client.ErrorListener l)))

    (testing "passes `errorOccurred`"
      (is (nil? (.errorOccurred l conn "test error")))
      (is (= {:type :error-occurred
              :connection conn
              :error "test error"}
             @recv)))

    (testing "passes `exceptionOccurred`"
      (let [ex (ex-info "test error" {})]
        (is (nil? (.exceptionOccurred l conn ex)))
        (is (= {:type :exception-occurred
                :connection conn
                :exception ex}
               @recv))))

    (testing "passes `flowControlProcessed`"
      (is (nil? (.flowControlProcessed l conn nil
                                       "test.subject" ErrorListener$FlowControlSource/HEARTBEAT)))
      (is (= {:type :flow-control-processed
              :connection conn
              :subscription nil
              :subject "test.subject"
              :flow-control-source ErrorListener$FlowControlSource/HEARTBEAT}
             @recv)))

    (testing "passes `heartbeatAlarm`"
      (is (nil? (.heartbeatAlarm l conn nil 1234 5678)))
      (is (= {:type :heartbeat-alarm
              :connection conn
              :subscription nil
              :last-stream-seq 1234
              :last-consumer-seq 5678}
             @recv)))

    (testing "passes `messageDiscarded`"
      (is (nil? (.messageDiscarded l conn nil)))
      (is (= {:type :message-discarded
              :connection conn
              :message nil}
             @recv)))

    (testing "passes `pullStatusError`"
      (is (nil? (.pullStatusError l conn nil nil)))
      (is (= {:type :pull-status-error
              :connection conn
              :subscription nil
              :status nil}
             @recv)))

    (testing "passes `pullStatusWarning`"
      (is (nil? (.pullStatusWarning l conn nil nil)))
      (is (= {:type :pull-status-warning
              :connection conn
              :subscription nil
              :status nil}
             @recv)))

    (testing "passes `slowConsumerDetected`"
      (is (nil? (.slowConsumerDetected l conn nil)))
      (is (= {:type :slow-consumer-detected
              :connection conn
              :consumer nil}
             @recv)))

    (testing "passes `socketWriteTimeout`"
      (is (nil? (.socketWriteTimeout l conn)))
      (is (= {:type :socket-write-timeout
              :connection conn}
             @recv)))

    (testing "passes `unhandledStatus`"
      (is (nil? (.unhandledStatus l conn nil nil)))
      (is (= {:type :unhandled-status
              :connection conn
              :subscription nil
              :status nil}
             @recv)))))

(deftest integration-test
  (with-open [conn (h/make-connection)]
    (testing "can connect to server"
      (is (sut/connection? conn)))

    (let [subject "test.nats.1"
          recv (atom [])
          s (sut/subscribe conn subject (partial swap! recv conj) {})]
      
      (testing "can subscribe and publish"
        (is (some? s))
        (is (nil? (sut/publish conn subject "Test message" {}))))

      (testing "invokes handler on received message"
        (is (not= :timeout (h/wait-until #(not-empty @recv) 1000 :timeout)))
        (is (= 1 (count @recv))))

      (testing "can unsubscribe"
        (is (some? (sut/unsubscribe s)))))

    (testing "applies serializer and deserializer"
      (let [subject "test.nats.2"
            recv (atom [])
            msg {:message "Another test"}
            sub (sut/subscribe conn subject (partial swap! recv conj)
                               {:deserializer sut/from-edn})]
        (is (nil? (sut/publish conn subject msg {:serializer sut/to-edn})))
        (is (not= :timeout (h/wait-until #(not-empty @recv) 1000 :timeout)))
        (is (= [msg] @recv))
        (is (some? (sut/unsubscribe sub)))))

    (testing "can close connection"
      (is (nil? (.close conn))))))

(deftest edn
  (let [msg {:key "value"}]
    (testing "can convert to edn message and back"
      (is (= msg (-> (sut/to-edn msg)
                     (String.)
                     (edn/read-string)))))

    (testing "can parse edn message"
      (is (= msg (-> (reify Message
                       (getData [this]
                         (sut/to-edn msg)))
                     (sut/from-edn)))))))
