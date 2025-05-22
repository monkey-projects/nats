(ns monkey.nats.jetstream.mgmt-test
  (:require [monkey.nats.jetstream.mgmt :as sut]
            [clojure.test :refer [deftest testing is]])
  (:import [io.nats.client.api AckPolicy StorageType]
           [java.time Duration]))

(deftest make-options
  (testing "sets name"
    (is (= "test-stream" (.getName (sut/make-options {:name "test-stream"})))))

  (testing "sets storage type"
    (is (= StorageType/File (.getStorageType (sut/make-options {:storage-type :file})))))

  (testing "sets subjects"
    (is (= ["a" "b"]
           (-> {:subjects ["a" "b"]}
               (sut/make-options)
               (.getSubjects)
               (seq))))))

(deftest consumer-options
  (let [opts (sut/consumer-options {:ack-policy :all
                                    :description "test options"
                                    :inactive-threshold 1000000000 ; Wtf, nanoseconds?
                                    :filter-subjects ["sub-a" "sub-b"]
                                    :durable-name "test-durable"})]
    (testing "sets description"
      (is (= "test options"
             (.getDescription opts))))

    (testing "sets ack policy"
      (is (= AckPolicy/All
             (.getAckPolicy opts))))

    (testing "sets inactive threshold"
      (is (= (Duration/ofMillis 1000)
             (.getInactiveThreshold opts))))

    (testing "sets filter-subjects"
      (is (= ["sub-a" "sub-b"]
             (.getFilterSubjects opts))))

    (testing "sets durable name"
      (is (= "test-durable"
             (.getDurable opts))))))
