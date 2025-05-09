(ns monkey.nats.jetstream.mgmt-test
  (:require [monkey.nats.jetstream.mgmt :as sut]
            [clojure.test :refer [deftest testing is]])
  (:import io.nats.client.api.StorageType))

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

