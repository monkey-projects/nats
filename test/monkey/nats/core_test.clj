(ns monkey.nats.core-test
  (:require [clojure.test :refer [deftest testing is]]
            [babashka.fs :as fs]
            [clojure.edn :as edn]
            [monkey.nats.core :as sut]
            [config.core :as cc])
  (:import io.nats.client.Message))

(def url (:nats-url cc/env))
(def creds (:nats-creds cc/env))

(defn- add-creds [conf]
  (let [k (if (fs/exists? creds) :credential-path :static-creds)]
    (assoc conf k creds)))

(defn- wait-until [pred timeout rv]
  (let [start (System/currentTimeMillis)]
    (loop []
      (if-let [v (pred)]
        v
        (if (> (- (System/currentTimeMillis) start) timeout)
          rv
          (do
            (Thread/sleep 100)
            (recur)))))))

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
                   (.getAuthHandler))))))

(deftest integration-test
  (let [conn (sut/make-connection (-> {:urls [url]
                                       :secure? true
                                       :verbose? true}
                                      (add-creds)))]
    (testing "can connect to server"
      (is (sut/connection? conn)))

    (let [subject "test.nats.1"
          recv (atom [])
          s (sut/subscribe conn subject (partial swap! recv conj) {})]
      
      (testing "can subscribe and publish"
        (is (some? s))
        (is (nil? (sut/publish conn subject "Test message" {}))))

      (testing "invokes handler on received message"
        (is (not= :timeout (wait-until #(not-empty @recv) 1000 :timeout)))
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
        (is (not= :timeout (wait-until #(not-empty @recv) 1000 :timeout)))
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
