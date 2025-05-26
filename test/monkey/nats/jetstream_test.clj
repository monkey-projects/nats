(ns monkey.nats.jetstream-test
  (:require [monkey.nats
             [core :as c]
             [jetstream :as sut]
             [test-helpers :as h]]
            [monkey.nats.jetstream.mgmt :as jsm]
            [clojure.test :refer [deftest testing is]]))

(deftest jetstream-integration
  (with-open [conn (h/make-connection)]
    (let [mgmt (jsm/make-mgmt conn)
          stream (jsm/add-stream mgmt
                                 {:name h/stream
                                  :storage-type :file
                                  :subjects ["test.*"]})
          js (sut/make-jetstream conn)
          subject "test.js-1"
          id "test-consumer"
          c (jsm/make-consumer mgmt
                               stream
                               {:filter-subjects [subject]
                                :ack-policy :all
                                :durable-name id})
          recv (atom [])
          handler (fn [msg]
                    (swap! recv conj msg)
                    (sut/ack msg))]
      (testing "can configure jetstream consumer"
        (is (some? c)))

      (let [ctx (sut/consumer-ctx js stream id)]
        (testing "consumer"
          (let [consumer (sut/consume ctx handler)
                msg {:message "test message"}]
            (testing "can publish"
              (is (some? (sut/publish js subject msg {}))))
            
            (testing "can receive after publish"
              (is (not= ::timeout (h/wait-until #(not-empty @recv) 1000 ::timeout)))
              (is (= 1 (count @recv))))

            (testing "can stop and close consumer"
              (is (nil? (sut/stop consumer)))
              (is (nil? (sut/close consumer))))))

        (testing "fetcher"
          (let [fetcher (sut/fetch ctx
                                   {:no-wait-expires-in 1000
                                    :max-messages 1})
                msg {:message "test message"}]
            (testing "can publish"
              (is (some? (sut/publish js subject msg {}))))
            
            (testing "can fetch"
              (let [recv (fetcher)]
                (is (some? recv))
                (is (= msg (c/from-edn recv)))
                (is (nil? (sut/ack recv)))))

            (testing "can fetch after new publish"
              (let [evt {:message "another message"}]
                (is (some? (sut/publish js subject evt {})))
                (let [msg (fetcher)]
                  (is (= evt (c/from-edn msg)))
                  (is (nil? (sut/ack msg))))))

            (testing "can stop and close fetcher"
              (is (nil? (sut/close fetcher))))))

        (testing "`take-next`"
          (let [msg {:message "pending message"}]
            (is (some? (sut/publish js subject msg {})))
            (testing "receives next pending message"
              (is (= msg (sut/take-next ctx {:deserializer c/from-edn
                                             :timeout 1000})))))))

      (testing "can delete stream"
        (is (true? (jsm/delete-stream mgmt stream)))))))

(deftest consume-options
  (testing "can set group"
    (is (= "test-group" (-> {:group "test-group"}
                            (sut/consume-options)
                            (.getGroup))))))
